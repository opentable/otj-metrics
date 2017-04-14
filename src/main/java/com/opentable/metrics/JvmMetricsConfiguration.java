package com.opentable.metrics;

import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.management.MBeanServer;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.collect.ImmutableMap;

import com.opentable.metrics.jvm.CpuLoadGauge;
import com.opentable.metrics.jvm.NmtMetrics;

@Named
public class JvmMetricsConfiguration {
    private static final String base = "jvm";
    private final MetricRegistry metrics;
    private final MBeanServer mbs;
    private final NmtMetrics nmtMetrics;

    JvmMetricsConfiguration(final MetricRegistry metrics, final MBeanServer mbs) {
        this.metrics = metrics;
        this.mbs = mbs;
        nmtMetrics = new NmtMetrics(String.format("%s.nmt", base), metrics);
    }

    private static MetricSet namespace(String namespace, MetricSet metrics) {
        ImmutableMap.Builder<String, Metric> builder = ImmutableMap.builder();
        metrics.getMetrics().forEach((name, metric) ->
                builder.put(String.format("%s.%s.%s", base, namespace, name), metric));
        final ImmutableMap<String, Metric> built = builder.build();

        return () -> built;
    }

    @PostConstruct
    void postConstruct() {
        metrics.registerAll(namespace("bufpool", new BufferPoolMetricSet(mbs)));
        metrics.register(base + ".fd.used-ratio", new FileDescriptorRatioGauge());
        metrics.registerAll(namespace("gc", new GarbageCollectorMetricSet()));
        metrics.registerAll(namespace("mem", new MemoryUsageGaugeSet()));
        metrics.registerAll(namespace("class", new ClassLoadingGaugeSet()));
        metrics.registerAll(namespace("thread", new ThreadStatesGaugeSet()));
        metrics.register(base + ".cpu.load", new CpuLoadGauge());

        nmtMetrics.register();
    }
}
