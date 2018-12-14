package com.opentable.metrics.graphite;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.GraphiteSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtGraphiteReporter extends ScheduledReporter {

    private static final Logger log = LoggerFactory.getLogger(OtGraphiteReporter.class);

    private final GraphiteSender graphite;
    private final Clock clock;
    private final String prefix;
    private final Map<String, Long> reportedCounters = new ConcurrentHashMap<String, Long>();
    private double countFactor = 1.0;

    public OtGraphiteReporter(MetricRegistry registry,
                               GraphiteSender graphite,
                               String prefix) {
        super(registry, "opentable-graphite-reporter", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        this.graphite = graphite;
        this.clock = Clock.defaultClock();
        this.prefix = prefix;
    }

    private void reportTimer(String name, Timer timer, long timestamp) throws IOException {
        final Snapshot snapshot = timer.getSnapshot();
        graphite.send(prefix(name, "max"), format(convertDuration(snapshot.getMax())), timestamp);
        graphite.send(prefix(name, "mean"), format(convertDuration(snapshot.getMean())), timestamp);
        graphite.send(prefix(name, "min"), format(convertDuration(snapshot.getMin())), timestamp);
        graphite.send(prefix(name, "stddev"), format(convertDuration(snapshot.getStdDev())), timestamp);
        graphite.send(prefix(name, "p50"), format(convertDuration(snapshot.getMedian())), timestamp);
        graphite.send(prefix(name, "p75"), format(convertDuration(snapshot.get75thPercentile())), timestamp);
        graphite.send(prefix(name, "p95"), format(convertDuration(snapshot.get95thPercentile())), timestamp);
        graphite.send(prefix(name, "p98"), format(convertDuration(snapshot.get98thPercentile())), timestamp);
        graphite.send(prefix(name, "p99"), format(convertDuration(snapshot.get99thPercentile())), timestamp);
        graphite.send(prefix(name, "p999"), format(convertDuration(snapshot.get999thPercentile())), timestamp);
        reportMetered(name, timer, timestamp);
    }

    private void reportMetered(String name, Metered meter, long timestamp) throws IOException {
        this.reportCounter(name, meter.getCount(), timestamp);
        graphite.send(prefix(name, "m1_rate"), format(convertRate(meter.getOneMinuteRate())), timestamp);
        graphite.send(prefix(name, "m5_rate"), format(convertRate(meter.getFiveMinuteRate())), timestamp);
        graphite.send(prefix(name, "m15_rate"), format(convertRate(meter.getFifteenMinuteRate())), timestamp);
        graphite.send(prefix(name, "mean_rate"), format(convertRate(meter.getMeanRate())), timestamp);
    }

    private void reportHistogram(String name, Histogram histogram, long timestamp) throws IOException {
        final Snapshot snapshot = histogram.getSnapshot();
        this.reportCounter(name, histogram.getCount(), timestamp);
        graphite.send(prefix(name, "max"), format(snapshot.getMax()), timestamp);
        graphite.send(prefix(name, "mean"), format(snapshot.getMean()), timestamp);
        graphite.send(prefix(name, "min"), format(snapshot.getMin()), timestamp);
        graphite.send(prefix(name, "stddev"), format(snapshot.getStdDev()), timestamp);
        graphite.send(prefix(name, "p50"), format(snapshot.getMedian()), timestamp);
        graphite.send(prefix(name, "p75"), format(snapshot.get75thPercentile()), timestamp);
        graphite.send(prefix(name, "p95"), format(snapshot.get95thPercentile()), timestamp);
        graphite.send(prefix(name, "p98"), format(snapshot.get98thPercentile()), timestamp);
        graphite.send(prefix(name, "p99"), format(snapshot.get99thPercentile()), timestamp);
        graphite.send(prefix(name, "p999"), format(snapshot.get999thPercentile()), timestamp);
    }

    private void reportCounter(String name, Counter counter, long timestamp) throws IOException {
        this.reportCounter(name, counter.getCount(), timestamp);
    }

    private void reportCounter(String name, long value, long timestamp) throws IOException {
        graphite.send(prefix(name, "count"), format(value), timestamp);
        final long diff = value - Optional.ofNullable(reportedCounters.put(name, value)).orElse(0L);
        if (diff != 0L) {
            graphite.send(prefix(name, "hits"), format(diff), timestamp);
            graphite.send(prefix(name, "cps"), format(diff * countFactor), timestamp);
        }
    }

    private void reportGauge(String name, Gauge gauge, long timestamp) throws IOException {
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
        }
        return null;
    }

    private String prefix(String... components) {
        return MetricRegistry.name(prefix, components);
    }

    private String format(long n) {
        return Long.toString(n);
    }

    private String format(double v) {
        return String.format(Locale.US, "%2.2f", v);
    }

    public void internalReport(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) throws IOException {

        final long timestamp = clock.getTime() / 1000;

        for (Entry<String, Gauge> entry : gauges.entrySet()) {
            reportGauge(entry.getKey(), entry.getValue(), timestamp);
        }

        for (Entry<String, Counter> entry : counters.entrySet()) {
            reportCounter(entry.getKey(), entry.getValue(), timestamp);
        }

        for (Entry<String, Histogram> entry : histograms.entrySet()) {
            reportHistogram(entry.getKey(), entry.getValue(), timestamp);
        }

        for (Entry<String, Meter> entry : meters.entrySet()) {
            reportMetered(entry.getKey(), entry.getValue(), timestamp);
        }

        for (Entry<String, Timer> entry : timers.entrySet()) {
            reportTimer(entry.getKey(), entry.getValue(), timestamp);
        }
    }

    @Override
    public void start(long period, TimeUnit unit) {
        this.countFactor = 1.0 / (double)unit.toSeconds(period);
        super.start(period, unit);
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            try {
                graphite.close();
            } catch (IOException e) {
                log.warn("Error disconnecting from Graphite", graphite, e);
            }
        }
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
        SortedMap<String, Counter> counters,
        SortedMap<String, Histogram> histograms,
        SortedMap<String, Meter> meters,
        SortedMap<String, Timer> timers) {
        try {
            if (!graphite.isConnected()) {
                graphite.connect();
            }
            this.internalReport(gauges, counters, histograms, meters, timers);
            graphite.flush();
        } catch (IOException e) {
            log.warn("Unable to report to Graphite", graphite, e);
            try {
                graphite.close();
            } catch (IOException e1) {
                log.warn("Error closing Graphite", graphite, e1);
            }
        } finally {
            try {
                graphite.close();
            } catch (IOException e1) {
                log.warn("Error closing Graphite", graphite, e1);
            }
        }
    }
}
