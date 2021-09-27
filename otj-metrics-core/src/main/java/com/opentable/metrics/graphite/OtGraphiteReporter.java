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

import static com.codahale.metrics.MetricAttribute.COUNT;
import static com.codahale.metrics.MetricAttribute.M15_RATE;
import static com.codahale.metrics.MetricAttribute.M1_RATE;
import static com.codahale.metrics.MetricAttribute.M5_RATE;
import static com.codahale.metrics.MetricAttribute.MAX;
import static com.codahale.metrics.MetricAttribute.MEAN;
import static com.codahale.metrics.MetricAttribute.MEAN_RATE;
import static com.codahale.metrics.MetricAttribute.MIN;
import static com.codahale.metrics.MetricAttribute.P50;
import static com.codahale.metrics.MetricAttribute.P75;
import static com.codahale.metrics.MetricAttribute.P95;
import static com.codahale.metrics.MetricAttribute.P98;
import static com.codahale.metrics.MetricAttribute.P99;
import static com.codahale.metrics.MetricAttribute.P999;
import static com.codahale.metrics.MetricAttribute.STDDEV;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricAttribute;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOTE: This is a fork of metrics-graphite's GraphiteReporter.
 * NOTE: All changes carefully documented here:
 *  <ul>
 *    <li>{@link OtGraphiteReporter#reportedCounters}</li>
 *    <li>{@link OtGraphiteReporter#countFactor}</li>
 *    <li>{@link OtGraphiteReporter#reportCounterExtended(String, Counter, long)}</li>
 *    <li>{@link OtGraphiteReporter#start(long, TimeUnit)}</li>
 *    <li>{@link OtGraphiteReporter#reportMeteredExtended(String, Metered, long)}</li>
 *    <li>{@link OtGraphiteReporter#reportHistogramExtended(String, Histogram, long)}</li>
 *  <ul/>
 * NOTE: When Dropwizard versions change, be careful to painstakingly report the changes
 *
 * A reporter which publishes metric values to a Graphite server.
 *
 * @see <a href="http://graphite.wikidot.com/">Graphite - Scalable Realtime Graphing</a>
 */
public class OtGraphiteReporter extends GraphiteReporter {

    /**
     * State of the counters from the last report. Used to calculate derivative
     * See {@link #reportCounterExtended(String, Counter, long)}
     */
    private final Map<String, Long> reportedCounters = new ConcurrentHashMap<>();

    /**
     * 1 / (Report period in seconds) to calculate cps.
     * See {@link #reportCounterExtended(String, Counter, long)}
     */
    private volatile double countFactor = 1.0;

    private static final Logger LOGGER = LoggerFactory.getLogger(OtGraphiteReporter.class);

    private final GraphiteSender graphite;
    private final Clock clock;
    private final String prefix;

    /**
     * Creates a new {@link OtGraphiteReporter} instance.
     *
     * @param registry               the {@link MetricRegistry} containing the metrics this
     *                               reporter will report
     * @param graphite               the {@link GraphiteSender} which is responsible for sending metrics to a Carbon server
     *                               via a transport protocol
     * @param clock                  the instance of the time. Use {@link Clock#defaultClock()} for the default
     * @param prefix                 the prefix of all metric names (may be null)
     * @param rateUnit               the time unit of in which rates will be converted
     * @param durationUnit           the time unit of in which durations will be converted
     * @param filter                 the filter for which metrics to report
     * @param executor               the executor to use while scheduling reporting of metrics (may be null).
     * @param shutdownExecutorOnStop if true, then executor will be stopped in same time with this reporter
     */
    protected OtGraphiteReporter(MetricRegistry registry,
                                 GraphiteSender graphite,
                                 Clock clock,
                                 String prefix,
                                 TimeUnit rateUnit,
                                 TimeUnit durationUnit,
                                 MetricFilter filter,
                                 ScheduledExecutorService executor,
                                 boolean shutdownExecutorOnStop,
                                 Set<MetricAttribute> disabledMetricAttributes) {
        super(registry,
                graphite,
                clock,
                prefix,
                rateUnit,
                durationUnit,
                filter,
                executor,
                shutdownExecutorOnStop,
                disabledMetricAttributes,
                false);
        this.graphite = graphite;
        this.clock = clock;
        this.prefix = prefix;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        final long timestamp = clock.getTime() / 1000;

        // oh it'd be lovely to use Java 7 here
        try {
            graphite.connect();

            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                reportGauge(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                reportCounterExtended(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                reportHistogramExtended(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                reportMeteredExtended(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                reportTimer(entry.getKey(), entry.getValue(), timestamp);
            }
            graphite.flush();
        } catch (IOException e) {
            LOGGER.warn("Unable to report to Graphite", graphite, e);
        } finally {
            try {
                graphite.close();
            } catch (IOException e1) {
                LOGGER.warn("Error closing Graphite", graphite, e1);
            }
        }
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            try {
                graphite.close();
            } catch (IOException e) {
                LOGGER.debug("Error disconnecting from Graphite", graphite, e);
            }
        }
    }

    private void reportTimer(String name, Timer timer, long timestamp) throws IOException {
        final Snapshot snapshot = timer.getSnapshot();
        sendIfEnabled(MAX, name, convertDuration(snapshot.getMax()), timestamp);
        sendIfEnabled(MEAN, name, convertDuration(snapshot.getMean()), timestamp);
        sendIfEnabled(MIN, name, convertDuration(snapshot.getMin()), timestamp);
        sendIfEnabled(STDDEV, name, convertDuration(snapshot.getStdDev()), timestamp);
        sendIfEnabled(P50, name, convertDuration(snapshot.getMedian()), timestamp);
        sendIfEnabled(P75, name, convertDuration(snapshot.get75thPercentile()), timestamp);
        sendIfEnabled(P95, name, convertDuration(snapshot.get95thPercentile()), timestamp);
        sendIfEnabled(P98, name, convertDuration(snapshot.get98thPercentile()), timestamp);
        sendIfEnabled(P99, name, convertDuration(snapshot.get99thPercentile()), timestamp);
        sendIfEnabled(P999, name, convertDuration(snapshot.get999thPercentile()), timestamp);
        reportMeteredExtended(name, timer, timestamp);
    }

    /**
     * We replaced {@code sendIfEnabled(COUNT, name, meter.getCount(), timestamp)} with the
     * call {@link OtGraphiteReporter#reportCounterExtended(String, Counter, long)}
     */
    private void reportMeteredExtended(String name, Metered meter, long timestamp) throws IOException {
        if (!getDisabledMetricAttributes().contains(COUNT)) {
            reportCounterExtended(name, meter.getCount(), timestamp);
        }
        sendIfEnabled(M1_RATE, name, convertRate(meter.getOneMinuteRate()), timestamp);
        sendIfEnabled(M5_RATE, name, convertRate(meter.getFiveMinuteRate()), timestamp);
        sendIfEnabled(M15_RATE, name, convertRate(meter.getFifteenMinuteRate()), timestamp);
        sendIfEnabled(MEAN_RATE, name, convertRate(meter.getMeanRate()), timestamp);
    }

    /**
     * We replaced {@code sendIfEnabled(COUNT, name, histogram.getCount(), timestamp)} with the
     * call {@link OtGraphiteReporter#reportCounterExtended(String, Counter, long)}
     */
    private void reportHistogramExtended(String name, Histogram histogram, long timestamp) throws IOException {
        final Snapshot snapshot = histogram.getSnapshot();
        if (!getDisabledMetricAttributes().contains(COUNT)) {
            reportCounterExtended(name, histogram.getCount(), timestamp);
        }
        sendIfEnabled(MAX, name, snapshot.getMax(), timestamp);
        sendIfEnabled(MEAN, name, snapshot.getMean(), timestamp);
        sendIfEnabled(MIN, name, snapshot.getMin(), timestamp);
        sendIfEnabled(STDDEV, name, snapshot.getStdDev(), timestamp);
        sendIfEnabled(P50, name, snapshot.getMedian(), timestamp);
        sendIfEnabled(P75, name, snapshot.get75thPercentile(), timestamp);
        sendIfEnabled(P95, name, snapshot.get95thPercentile(), timestamp);
        sendIfEnabled(P98, name, snapshot.get98thPercentile(), timestamp);
        sendIfEnabled(P99, name, snapshot.get99thPercentile(), timestamp);
        sendIfEnabled(P999, name, snapshot.get999thPercentile(), timestamp);
    }

    private void sendIfEnabled(MetricAttribute type, String name, double value, long timestamp) throws IOException {
        if (getDisabledMetricAttributes().contains(type)) {
            return;
        }
        graphite.send(prefix(name, type.getCode()), format(value), timestamp);
    }

    private void sendIfEnabled(MetricAttribute type, String name, long value, long timestamp) throws IOException {
        if (getDisabledMetricAttributes().contains(type)) {
            return;
        }
        graphite.send(prefix(name, type.getCode()), format(value), timestamp);
    }

    /**
     * For each counter reports additional metrics:
     * <ul>
     *   <li>{@code <name>.hits} - counter derivative</li>
     *   <li>{@code <name>.cps}  - count per second</li>
     *</ul>
     * Updates {@link #reportedCounters} with new value.
     *
     * @param name  name of the counter
     * @param counter counter
     * @param timestamp report timestamp
     * @throws IOException
     */
    private void reportCounterExtended(String name, Counter counter, long timestamp) throws IOException {
        this.reportCounterExtended(name, counter.getCount(), timestamp);
    }

    private void reportCounterExtended(String name, long value, long timestamp) throws IOException {
        graphite.send(prefix(name, COUNT.getCode()), format(value), timestamp);
        final long diff = value - Optional.ofNullable(reportedCounters.put(name, value)).orElse(0L);
        if (diff != 0L) {
            graphite.send(prefix(name, "hits"), format(diff), timestamp);
            graphite.send(prefix(name, "cps"), format(diff * countFactor), timestamp);
        }
    }

    private void reportGauge(String name, Gauge<?> gauge, long timestamp) throws IOException {
        final String value = format(gauge.getValue());
        if (value != null) {
            graphite.send(prefix(name), value, timestamp);
        }
    }

    private String format(Object o) {
        if (o instanceof Float) {
            return format(((Float) o).doubleValue());
        } else if (o instanceof Double) {
            return format(((Double) o).doubleValue());
        } else if (o instanceof Byte) {
            return format(((Byte) o).longValue());
        } else if (o instanceof Short) {
            return format(((Short) o).longValue());
        } else if (o instanceof Integer) {
            return format(((Integer) o).longValue());
        } else if (o instanceof Long) {
            return format(((Long) o).longValue());
        } else if (o instanceof BigInteger) {
            return format(((BigInteger) o).doubleValue());
        } else if (o instanceof BigDecimal) {
            return format(((BigDecimal) o).doubleValue());
        } else if (o instanceof Boolean) {
            return format(((Boolean) o) ? 1 : 0);
        }
        return null;
    }

    private String prefix(String... components) {
        return MetricRegistry.name(prefix, components);
    }

    private String format(long n) {
        return Long.toString(n);
    }

    protected String format(double v) {
        // the Carbon plaintext format is pretty underspecified, but it seems like it just wants
        // US-formatted digits
        return String.format(Locale.US, "%2.2f", v);
    }

    @Override
    public void start(long period, TimeUnit unit) {
        this.countFactor = 1.0 / (double)unit.toMillis(period) * 1000.0;
        super.start(period, unit);
    }

}
