package com.opentable.metrics;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.graphite.GraphiteReporter;
import com.opentable.metrics.health.HealthConfiguration;

@Configuration
@Import({
        JettyServerMetricsModule.class,
        JvmMetricsConfiguration.class,
        HealthConfiguration.class,
        GraphiteReporter.class,
})
public final class DefaultMetricsConfiguration {}
