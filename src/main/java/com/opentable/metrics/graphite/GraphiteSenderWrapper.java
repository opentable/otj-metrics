/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.metrics.graphite;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.concurrent.GuardedBy;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteSender;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("resource")
public class GraphiteSenderWrapper implements GraphiteSender, Closeable, MetricSet {
    private static final Logger LOG = LoggerFactory.getLogger(GraphiteSenderWrapper.class);

    /**
     * We implement an unconditional periodic reconnect to ameliorate ELB black hole issues and other
     * difficult-to-trace problems related to our Graphite infrastructure seeming to intermittently lose
     * metrics.
     *
     * <p>
     * This is an unfortunate hack, and it would be nice to be able to remove it.
     */
    private static final Duration RECONNECT_PERIOD = Duration.ofHours(1);

    static final String DETECTED_CONNECTION_FAILURES = "reporter-wrapper.detected-connection-failures";
    static final String CONNECTION_CLOSE = "reporter-wrapper.connection-close";

    private final Counter connectionFailures = new Counter();
    private final Counter connectionCloses = new Counter();
    private final Supplier<InetSocketAddress> address;

    @GuardedBy("this")
    private Graphite delegate; // either connect()ed or null
    @GuardedBy("this")
    private Instant lastReconnect;

    GraphiteSenderWrapper(Supplier<InetSocketAddress> address) {
        this.address = address;
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return ImmutableMap.of(
                DETECTED_CONNECTION_FAILURES, connectionFailures,
                CONNECTION_CLOSE, connectionCloses
        );
    }

    @Override
    public synchronized void close() throws IOException {
        if (delegate != null) {
            delegate.close();
        }
        delegate = null;
        connectionCloses.inc();
    }

    @Override
    public synchronized void connect() throws IllegalStateException, IOException {
        delegate(); // either we are connected or we throw
    }

    @Override
    public synchronized void send(String name, String value, long timestamp) throws IOException {
        delegate().send(name, value, timestamp);
    }

    @Override
    public synchronized void flush() throws IOException {
        delegate().flush();
    }

    @Override
    public synchronized boolean isConnected() {
        // If it's not already connected, don't accidentally cause a connection attempt
        if (delegate == null) {
            return false;
        }
        try {
            return delegate().isConnected();
        } catch (IOException e) {
            LOG.debug("While testing Graphite connectivity", e);
            return false;
        }
    }

    @Override
    public synchronized int getFailures() {
        try {
            return delegate().getFailures();
        } catch (IOException e) {
            LOG.debug("While counting Graphite failures", e);
            return Ints.saturatedCast(connectionFailures.getCount());
        }
    }

    private synchronized Graphite delegate() throws IOException {
        final boolean explicitlyClosed = delegate == null;
        boolean needsReconnectFail = false;
        if (explicitlyClosed || (needsReconnectFail = needsReconnectFail(delegate)) || needsReconnectPeriodic()) { //NOPMD
            if (needsReconnectFail) {
                connectionFailures.inc();
                LOG.warn("bad graphite state; recycling; connected {}, failures {}; counter {}; last @ {}",
                        delegate == null ? "UNKNOWN" : delegate.isConnected(),
                        delegate == null ? "UNKNOWN" : delegate.getFailures(),
                        connectionFailures.getCount(),
                        lastReconnect);
            }

            // Spin up new one
            Graphite newGraphite = new Graphite(address.get());
            newGraphite.connect();

            // Close the old one
            if (delegate != null) {
                try {
                    delegate.close();
                } catch (Exception e) {
                    LOG.error("Failed to close old graphite", e);
                }
            }

            // Publish it
            delegate = newGraphite;
            lastReconnect = Instant.now();
        }
        return delegate;
    }

    /**
     * @return Whether reconnect is merited because of erroneous state of {@code graphite}.
     */
    private static boolean needsReconnectFail(Graphite graphite) {
        return !graphite.isConnected() || graphite.getFailures() > 0;
    }

    /**
     * @return Whether reconnect is merited because of recycle period having elapsed.
     */
    private boolean needsReconnectPeriodic() {
        if (lastReconnect == null) {
            return false;
        }
        final Duration elapsed = Duration.between(lastReconnect, Instant.now());
        final boolean needsReconnect = elapsed.compareTo(RECONNECT_PERIOD) > 0;
        if (needsReconnect) {
            LOG.info("unconditionally recycling graphite sender: elapsed {} > thresh {}",
                    elapsed, RECONNECT_PERIOD);
        }
        return needsReconnect;
    }
}
