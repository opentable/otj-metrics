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
     * As of DropWizard 3.1.3+, this is PROBABLY not needed anymore, but the wrapper is maintained for providing delegation purposes
     * and for tracking connectionFailures and closes for diagnostics.
     * (In DropWizard 3.1.3+, each reporting period, a new socket (including DNS resolution) is created, metrics are sent, and
     * the socket is closed. All the previous issues occurred because of atttempts to reuse a socket)
     * Hence, this class is now mostly a "dumb" delegator, with little extra logic other than
     * metrics wrapping, and a restart per hour.
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

    GraphiteSenderWrapper(Supplier<InetSocketAddress> address, Graphite delegate) {
        this.address = address;
        this.delegate = delegate;
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
        try {
            delegate.close();
        } finally {
            connectionCloses.inc();
        }
    }

    @Override
    public synchronized void connect() throws IllegalStateException, IOException {
        try {
            maybeRecycle().connect(); // either we are connected or we throw
        } catch (IllegalStateException | IOException e){
            connectionFailures.inc();
            throw e;
        }
    }

    @Override
    public synchronized void send(String name, String value, long timestamp) throws IOException {
        delegate.send(name, value, timestamp);
    }

    @Override
    public synchronized void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public synchronized boolean isConnected() {
        // If it's not already connected, don't accidentally cause a connection attempt
       return delegate.isConnected();
    }

    @Override
    public synchronized int getFailures() {
        return delegate.getFailures();
    }

    private synchronized Graphite maybeRecycle() throws IOException {
        if (needsReconnectPeriodic()) { //NOPMD
            // Spin up new one
            Graphite newGraphite = new Graphite(address.get());
            //newGraphite.connect();

            // Close the old one
            closeOldGraphite();

            // Publish it
            delegate = newGraphite;
            lastReconnect = Instant.now();
        }
        return delegate;
    }

    private void closeOldGraphite() {
        if (delegate != null) {
            try {
                delegate.close();
            } catch (Exception e) {
                LOG.error("Failed to close old graphite", e);
            }
        }
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
