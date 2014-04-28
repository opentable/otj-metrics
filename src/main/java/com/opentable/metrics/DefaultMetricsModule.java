package com.opentable.metrics;

import com.google.inject.AbstractModule;

public class DefaultMetricsModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        install (new JettyServerMetricsModule());
        install (new JvmMetricsModule());
    }
}
