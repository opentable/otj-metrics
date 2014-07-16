package com.opentable.metrics;

import com.google.inject.AbstractModule;

import com.opentable.metrics.health.HealthModule;

public class DefaultMetricsModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        install (new JettyServerMetricsModule());
        install (new JvmMetricsModule());
        install (new HealthModule());
    }
}
