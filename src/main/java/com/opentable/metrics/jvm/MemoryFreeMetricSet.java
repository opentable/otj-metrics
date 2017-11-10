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
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.google.common.collect.ImmutableMap;

/**
 * Adds {@code .free} gauges, based on max minus used, namespace'd as those in
 * {@link com.codahale.metrics.jvm.MemoryUsageGaugeSet}; thus, you can merge the metrics emitted by these classes.
 *
 * <p>
 * Add per-pool {@code .free} metrics if needed.
 */
public class MemoryFreeMetricSet implements MetricSet {
    private static final MemoryMXBean MEM = ManagementFactory.getMemoryMXBean();

    private final Map<String, Metric> metricMap;

    public MemoryFreeMetricSet() {
        final Gauge<Long> heapFree = free(MEM::getHeapMemoryUsage);
        final Gauge<Long> nonHeapFree = free(MEM::getNonHeapMemoryUsage);
        metricMap = ImmutableMap.of(
                "heap.free", heapFree,
                "non-heap.free", nonHeapFree,
                "total.free", sum(heapFree, nonHeapFree)
        );
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return metricMap;
    }

    private static Gauge<Long> free(final Supplier<MemoryUsage> usageSupplier) {
        return () -> {
            final MemoryUsage usage = usageSupplier.get();
            return usage.getMax() - usage.getUsed();
        };
    }

    private static Gauge<Long> sum(final Gauge<Long>... gauges) {
        final List<Gauge<Long>> list = Arrays.asList(gauges);
        return () -> list.stream().mapToLong(Gauge::getValue).sum();
    }
}
