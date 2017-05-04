package com.opentable.metrics.graphite;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import javax.annotation.concurrent.GuardedBy;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteSender;
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

    private final Counter connectionFailures;
    private final InetSocketAddress address;

    @GuardedBy("this")
    private Graphite delegate; // either connect()ed or null
    @GuardedBy("this")
    private Instant lastReconnect;

    GraphiteSenderWrapper(InetSocketAddress address) {
        this.address = address;
        this.connectionFailures = new Counter();
        connectionFailures.dec(); // initial connection isn't a failure
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return Collections.singletonMap(DETECTED_CONNECTION_FAILURES, connectionFailures);
    }

    @Override
    public synchronized void close() throws IOException {
        if (delegate != null) {
            delegate.close();
        }
        delegate = null;
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
        final boolean needsReconnectFail = needsReconnectFail(delegate);
        final boolean needsReconnectPeriodic = needsReconnectPeriodic();
        if (needsReconnectFail || needsReconnectPeriodic) {
            if (needsReconnectFail) {
                connectionFailures.inc();
                long failCount = connectionFailures.getCount();
                if (failCount > 0) {
                    LOG.warn("bad graphite state; recycling; connected {}, failures {}; counter {}",
                            delegate == null ? "UNKNOWN" : delegate.isConnected(),
                            delegate == null ? "UNKNOWN" : delegate.getFailures(),
                            failCount);
                }
            }

            // Spin up new one
            Graphite newGraphite = new Graphite(address);
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
        return graphite == null || !graphite.isConnected() || graphite.getFailures() > 0;
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
