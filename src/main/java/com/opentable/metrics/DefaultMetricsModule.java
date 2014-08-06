package com.opentable.metrics;

import com.google.inject.AbstractModule;

import com.opentable.metrics.health.HealthModule;

public final class DefaultMetricsModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        install (new JettyServerMetricsModule());
        install (new JvmMetricsModule());
        install (new HealthModule());
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
