package com.opentable.metrics;

import javax.management.MBeanServer;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
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
            metrics.registerAll(new BufferPoolMetricSet(mbs));
            metrics.register("fd-used-ratio", new FileDescriptorRatioGauge());
            metrics.registerAll(new GarbageCollectorMetricSet());
            metrics.registerAll(new MemoryUsageGaugeSet());
            metrics.registerAll(new ThreadStatesGaugeSet());
        }
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
