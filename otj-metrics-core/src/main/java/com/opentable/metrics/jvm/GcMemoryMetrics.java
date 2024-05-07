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
package com.opentable.metrics.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import com.opentable.metrics.AtomicLongGauge;
import com.opentable.metrics.FloatingPointHistogram;

/**
 * Thanks to Mike Bell for pointing out
 * <a href="http://www.fasterj.com/articles/gcnotifs.shtml">this reference</a>.
 */
@SuppressWarnings("restriction")
public class GcMemoryMetrics {
    private final String prefix;
    //@GuardedBy("this")
    private final MetricRegistry metricRegistry;
    /** {@link GarbageCollectionNotificationInfo#getGcName()} &rarr; {@link GcInfo#endTime} of last GC */
    //@GuardedBy("this")
    private final Map<String, Duration> lastEndTimes = new HashMap<>();

    public GcMemoryMetrics(final String prefix, final MetricRegistry metricRegistry) {
        this.prefix = prefix;
        this.metricRegistry = metricRegistry;
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc -> {
            final NotificationEmitter emitter = (NotificationEmitter) gc;
            emitter.addNotificationListener(this::listener, null, null);
        });
    }

    private void listener(final Notification notif, @SuppressWarnings("unused") final Object handback) { // parameter needed to match NotificationListener signature
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notif.getType())) {
            return;
        }
        final CompositeData data = (CompositeData) notif.getUserData();
        final GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(data);
        handle(info);
    }

    private synchronized void handle(final GarbageCollectionNotificationInfo info) {
        final String name = info.getGcName();
        final GcInfo gcInfo = info.getGcInfo();
        markMeter(name);
        final Duration duration = Duration.ofMillis(gcInfo.getDuration());
        final Duration endTime = Duration.ofMillis(gcInfo.getEndTime());
        updateTime(name, duration, endTime);
        putGauges(name, "before", gcInfo.getMemoryUsageBeforeGc());
        putGauges(name, "after",  gcInfo.getMemoryUsageAfterGc());
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

    /** Deprecated since the timer now tracks the rate. */
    @Deprecated
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

    /**
     * Update timer metric for individual GC runs as well as histogram indicating a percent ([0, 100]) time spent in
     * GC. End time is the duration since JVM startup to the end of this particular GC run. We instrument a percent
     * instead of a proportion because the {@link com.codahale.metrics.graphite.GraphiteReporter#format(double)}
     * provides only two fractional digits.
     */
    private void updateTime(final String gcName, final Duration duration, final Duration endTime) {
        Metric metric;

        // Timer metric for individual GC run.
        final String timerName = name(gcName, "timer");
        metric = metricRegistry.getMetrics().get(timerName);
        final Timer timer;
        if (metric == null) {
            timer = metricRegistry.timer(timerName);
        } else {
            timer = (Timer) metric;
        }
        timer.update(duration.toNanos(), TimeUnit.NANOSECONDS);

        // Percent time spent in GC.

        final Duration lastEndTime = lastEndTimes.getOrDefault(gcName, Duration.ZERO);
        final Duration vmTimeSinceLastGC = endTime.minus(lastEndTime);
        final double percent = 100. * (duration.toMillis()) / (vmTimeSinceLastGC.toMillis());
        lastEndTimes.put(gcName, endTime);

        final String percentName = name(gcName, "pct-time-in-gc");
        metric = metricRegistry.getMetrics().get(percentName);
        final FloatingPointHistogram percentHist;
        if (metric == null) {
            percentHist = metricRegistry.register(percentName,
                    new FloatingPointHistogram(new ExponentiallyDecayingReservoir(), 1e9));
        } else {
            percentHist = (FloatingPointHistogram) metric;
        }
        percentHist.update(percent);
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
