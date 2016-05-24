package com.opentable.metrics;

import javax.management.MBeanServer;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;

public final class JvmMetricsModule extends AbstractModule
{
    private static final String base = "jvm";

    @Override
    public void configure()
    {
        bind (JvmMetricsSets.class).asEagerSingleton();
    }

    static class JvmMetricsSets
    {
        @Inject
        JvmMetricsSets(MetricRegistry metrics, MBeanServer mbs)
        {
            metrics.registerAll(namespace("bufpool", new BufferPoolMetricSet(mbs)));
            metrics.register(base + ".fd.used-ratio", new FileDescriptorRatioGauge());
            metrics.registerAll(namespace("gc", new GarbageCollectorMetricSet()));
            metrics.registerAll(namespace("mem", new MemoryUsageGaugeSet()));
            metrics.registerAll(namespace("thread", new ThreadStatesGaugeSet()));
        }
    }

    private static MetricSet namespace(String namespace, MetricSet metrics) {
        ImmutableMap.Builder<String, Metric> builder = ImmutableMap.builder();
        metrics.getMetrics().forEach((name, metric) ->
                builder.put(String.format("%s.%s.%s", base, namespace, name), metric));
        final ImmutableMap<String, Metric> built = builder.build();

        return () -> built;
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj != null && getClass().equals(obj.getClass());
    }
}
