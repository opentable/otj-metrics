package com.opentable.metrics.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javax.annotation.concurrent.GuardedBy;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.google.common.collect.ImmutableMap;

public class MemoryFreeMetricSet implements MetricSet {
    private static final MemoryMXBean MEM = ManagementFactory.getMemoryMXBean();

    private final Map<String, Metric> metricMap;

    public MemoryFreeMetricSet() {
        final MemoryFree heapFree = new UsageMemoryFree(MEM::getHeapMemoryUsage);
        final MemoryFree nonHeapFree = new UsageMemoryFree(MEM::getNonHeapMemoryUsage);
        final MemoryFree totalFree = new CombinedMemoryFree(heapFree, nonHeapFree);
        final ImmutableMap.Builder<String, Metric> builder = ImmutableMap.builder();
        put(builder, "heap", heapFree);
        put(builder, "non-heap", nonHeapFree);
        put(builder, "total", totalFree);
        metricMap = builder.build();
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return metricMap;
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals") // "remaining"
    private static void put(
            final ImmutableMap.Builder<String, Metric> builder,
            final String prefix,
            final MemoryFree free) {
        builder.put(name(prefix, "free", "bytes"), (Gauge<Long>) free::getBytesFree);
        builder.put(name(prefix, "free", "rate"), free.getRate());
        builder.put(name(prefix, "remaining", "m15_seconds"), (Gauge<Long>) free::getFifteenMinuteSecondsRemaining);
        builder.put(name(prefix, "remaining", "m5_seconds"), (Gauge<Long>) free::getFiveMinuteSecondsRemaining);
        builder.put(name(prefix, "remaining", "m1_seconds"), (Gauge<Long>) free::getOneMinuteSecondsRemaining);
        builder.put(name(prefix, "remaining", "mean_seconds"), (Gauge<Long>) free::getMeanSecondsRemaining);
    }

    private static String name(final String... parts) {
        return String.join(".", parts);
    }

    /**
     * If free bytes rate is ever 0, the seconds remaining methods will return {@code null}.
     */
    private interface MemoryFree {
        /** Return bytes free. */
        long getBytesFree();

        /** Return rate at which bytes free changes. */
        Meter getRate();

        /**
         * Return estimated seconds remaining until memory exhausted, based on quotient of bytes free and
         * 15-minute EWMA rate of change in bytes free.
         */
        Long getFifteenMinuteSecondsRemaining();

        /**
         * Return estimated seconds remaining until memory exhausted, based on quotient of bytes free and
         * 5-minute EWMA rate of change in bytes free.
         */
        Long getFiveMinuteSecondsRemaining();

        /**
         * Return estimated seconds remaining until memory exhausted, based on quotient of bytes free and
         * 1-minute EWMA rate of change in bytes free.
         */
        Long getOneMinuteSecondsRemaining();

        /**
         * Return estimated seconds remaining until memory exhausted, based on quotient of bytes free and
         * mean rate of change in bytes free.
         */
        Long getMeanSecondsRemaining();
    }

    private static class SuppliedMemoryFree implements MemoryFree {
        private final Meter rate = new Meter();
        private final LongSupplier free;
        @GuardedBy("this")
        private long lastFree;

        SuppliedMemoryFree(final LongSupplier free) {
            this.free = free;
            lastFree = getBytesFree();
        }

        @Override
        public final long getBytesFree() {
            return free.getAsLong();
        }

        @Override
        public Meter getRate() {
            return rate;
        }

        @Override
        public synchronized Long getFifteenMinuteSecondsRemaining() {
            return getSecondsRemaining(rate::getFifteenMinuteRate);
        }

        @Override
        public synchronized Long getFiveMinuteSecondsRemaining() {
            return getSecondsRemaining(rate::getFiveMinuteRate);
        }

        @Override
        public synchronized Long getOneMinuteSecondsRemaining() {
            return getSecondsRemaining(rate::getOneMinuteRate);
        }

        @Override
        public synchronized Long getMeanSecondsRemaining() {
            return getSecondsRemaining(rate::getMeanRate);
        }

        private long mark() {
            final long free = getBytesFree();
            rate.mark(free - lastFree);
            return lastFree = free;
        }

        private Long getSecondsRemaining(final DoubleSupplier f) {
            final long free = mark();
            final double rate = f.getAsDouble();
            if (rate >= 0) {
                // If rate is 0, then the quotient will be undefined.  So we return null.
                // If rate is positive, then free space is increasing.  So we have no meaningful estimate as to when
                // we will exhaust memory usage (e.g., we can't answer "never").  So we return null.
                return null;
            }
            // Rate is negative (free space is decreasing).  We want to give a positive result, so we flip the sign.
            return -Math.round(((double) free) / rate);
        }
    }

    private static class UsageMemoryFree extends SuppliedMemoryFree {
        UsageMemoryFree(final Supplier<MemoryUsage> usage) {
            super(() -> usage.get().getMax() - usage.get().getUsed());
        }
    }

    private static class CombinedMemoryFree extends SuppliedMemoryFree {
        CombinedMemoryFree(final MemoryFree... mfs) {
            this(Arrays.asList(mfs));
        }

        private CombinedMemoryFree(final List<MemoryFree> mfs) {
            super(() -> mfs.stream().mapToLong(MemoryFree::getBytesFree).sum());
        }
    }
}
