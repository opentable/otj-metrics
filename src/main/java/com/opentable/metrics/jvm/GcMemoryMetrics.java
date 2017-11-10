package com.opentable.metrics.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.GuardedBy;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;

import com.opentable.metrics.AtomicLongGauge;

/**
 * Thanks to Mike Bell for pointing out
 * <a href="http://www.fasterj.com/articles/gcnotifs.shtml">this reference</a>.
 */
public class GcMemoryMetrics {

    private final String prefix;
    @GuardedBy("this")
    private final MetricRegistry metricRegistry;

    public GcMemoryMetrics(final String prefix, final MetricRegistry metricRegistry) {
        this.prefix = prefix;
        this.metricRegistry = metricRegistry;
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc -> {
            final NotificationEmitter emitter = (NotificationEmitter) gc;
            emitter.addNotificationListener(this::listener, null, null);
        });
    }

    private void listener(final Notification notif, final Object handback) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notif.getType())) {
            return;
        }
        final CompositeData data = (CompositeData) notif.getUserData();
        final GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(data);
        handle(info);
    }

    private synchronized void handle(final GarbageCollectionNotificationInfo info) {
        markMeter(info.getGcName());
        putGauges(info.getGcName(), "before", info.getGcInfo().getMemoryUsageBeforeGc());
        putGauges(info.getGcName(), "after",  info.getGcInfo().getMemoryUsageAfterGc());
    }

    private void putGauges(final String gcName, final String timePart, final Map<String, MemoryUsage> usages) {
        usages.forEach((poolName, usage) -> {
            putPoolGauge(gcName, timePart, poolName, "max",  usage::getMax);
            putPoolGauge(gcName, timePart, poolName, "used", usage::getUsed);
            putPoolGauge(gcName, timePart, poolName, "free", () -> free(usage));
        });
        putTotalGauge(gcName, timePart, "max",  sum(usages.values(), MemoryUsage::getMax));
        putTotalGauge(gcName, timePart, "used", sum(usages.values(), MemoryUsage::getUsed));
        putTotalGauge(gcName, timePart, "free", sum(usages.values(), GcMemoryMetrics::free));
    }

    private void markMeter(final String gcName) {
        final String meterName = name(gcName, "rate");
        final Metric metric = metricRegistry.getMetrics().get(meterName);
        final Meter meter;
        if (metric == null) {
            meter = metricRegistry.meter(meterName);
        } else {
            meter = (Meter) metric;
        }
        meter.mark();
    }

    private void putPoolGauge(
            final String gcName,
            final String timePart,
            final String poolName,
            final String name,
            final LongSupplier s) {
        putGauge(s, gcName, timePart, "pools", poolName, name);
    }

    private void putTotalGauge(final String gcName, final String timePart, final String name, final LongSupplier s) {
        putGauge(s, gcName, timePart, "total", name);
    }

    private void putGauge(final LongSupplier s, final String... nameParts) {
        final String gaugeName = name(nameParts);
        final Metric metric = metricRegistry.getMetrics().get(gaugeName);
        final AtomicLongGauge gauge;
        if (metric == null) {
            gauge = metricRegistry.register(gaugeName, new AtomicLongGauge());
        } else {
            gauge = (AtomicLongGauge) metric;
        }
        gauge.set(s.getAsLong());
    }

    private LongSupplier sum(final Collection<MemoryUsage> usages, final ToLongFunction<MemoryUsage> f) {
        return () -> usages.stream().mapToLong(f).sum();
    }

    private String name(final String... parts) {
        return Stream.concat(
                Stream.of(prefix),
                Arrays
                        .stream(parts)
                        .map(GcMemoryMetrics::normalize)
        ).collect(Collectors.joining("."));
    }

    private static String normalize(final String s) {
        return s.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static long free(final MemoryUsage usage) {
        return usage.getMax() - usage.getUsed();
    }
}
