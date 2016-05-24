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
            metrics.registerAll(prependJvm(new BufferPoolMetricSet(mbs)));
            metrics.register("jvm-fd-used-ratio", new FileDescriptorRatioGauge());
            metrics.registerAll(prependJvm(new GarbageCollectorMetricSet()));
            metrics.registerAll(prependJvm(new MemoryUsageGaugeSet()));
            metrics.registerAll(prependJvm(new ThreadStatesGaugeSet()));
        }
    }

    private static MetricSet prependJvm(MetricSet metrics) {
        ImmutableMap.Builder<String, Metric> builder = ImmutableMap.builder();
        metrics.getMetrics().forEach((name, metric) -> builder.put("jvm-" + name, metric));
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
