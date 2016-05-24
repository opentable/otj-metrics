package com.opentable.metrics.jvm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;

import com.opentable.jvm.Memory;
import com.opentable.jvm.Nmt;

/**
 * If JVM argument -XX:NativeMemoryTracking=summary is present, will produce NMT-related metrics.
 * If not, will return an empty map.
 */
public class NmtGaugeSet implements MetricSet {
    @Override
    public Map<String, Metric> getMetrics() {
        final Nmt nmt = Memory.getNmt();
        if (nmt == null) {
            return Collections.emptyMap();
        }
        final Map<String, Metric> gauges = new HashMap<>(2 * (nmt.categories.size() + 1));
        final BiConsumer<String, Nmt.Usage> add = (name, usage) -> {
            final String gaugeName = name.toLowerCase().replaceAll(" ", "-");
            gauges.put(gaugeName + ".reserved", (Gauge<Long>)() -> usage.reserved);
            gauges.put(gaugeName + ".committed", (Gauge<Long>)() -> usage.committed);
        };
        add.accept("total", nmt.total);
        nmt.categories.forEach(add);
        return gauges;
    }
}
