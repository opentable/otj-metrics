package com.opentable.metrics.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;

import com.opentable.metrics.AtomicLongGauge;

/**
 * Thanks to Mike Bell for pointing out
 * <a href="http://www.fasterj.com/articles/gcnotifs.shtml">this reference</a>.
 */
public class GcMemoryMetrics {
    private static final List<GarbageCollectorMXBean> GCS = ManagementFactory.getGarbageCollectorMXBeans();

    private final String prefix;
    @GuardedBy("this")
    private final MetricRegistry metricRegistry;

    public GcMemoryMetrics(final String prefix, final MetricRegistry metricRegistry) {
        this.prefix = prefix;
        this.metricRegistry = metricRegistry;
        GCS.forEach(gc -> {
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
        putGauges(info.getGcName(), "before", info.getGcInfo().getMemoryUsageBeforeGc());
        putGauges(info.getGcName(), "after",  info.getGcInfo().getMemoryUsageAfterGc());
    }

    private void putGauges(final String gcName, final String timePart, final Map<String, MemoryUsage> usages) {
        usages.forEach((poolName, usage) -> {
            putGauge(gcName, timePart, poolName, "max",  usage::getMax);
            putGauge(gcName, timePart, poolName, "used", usage::getUsed);
        });
        putGauge(gcName, timePart, "total", "max",  sum(usages.values(), MemoryUsage::getMax));
        putGauge(gcName, timePart, "total", "used", sum(usages.values(), MemoryUsage::getUsed));
    }

    private void putGauge(
            final String gcName,
            final String timePart,
            final String poolName,
            final String name,
            final LongSupplier s) {
        final String gaugeName = name(gcName, timePart, poolName, name);
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
        final List<String> normalized = Stream.concat(
                Stream.of(prefix),
                Arrays
                        .stream(parts)
                        .map(GcMemoryMetrics::normalize)
        ).collect(Collectors.toList());
        return String.join(".", normalized);
    }

    private static String normalize(final String s) {
        return s.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
