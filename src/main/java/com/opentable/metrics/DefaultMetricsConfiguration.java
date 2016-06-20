package com.opentable.metrics;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.graphite.GraphiteModule;
import com.opentable.metrics.health.HealthConfiguration;

@Configuration
@Import({
        JettyServerMetricsModule.class,
        JvmMetricsConfiguration.class,
        HealthConfiguration.class,
        GraphiteModule.class,
})
public final class DefaultMetricsConfiguration {}
