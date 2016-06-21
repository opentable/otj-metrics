package com.opentable.metrics;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.health.HealthConfiguration;

@Configuration
@Import({
        JettyServerMetricsConfiguration.class,
        JvmMetricsConfiguration.class,
        HealthConfiguration.class,
        GraphiteReporter.class,
})
public final class DefaultMetricsConfiguration {}