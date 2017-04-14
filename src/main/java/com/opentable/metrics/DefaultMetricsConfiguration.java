package com.opentable.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.graphite.GraphiteConfiguration;
import com.opentable.metrics.health.HealthConfiguration;

@Configuration
@Import({
        JettyServerMetricsConfiguration.class,
        JvmMetricsConfiguration.class,
        HealthConfiguration.class,
        GraphiteConfiguration.class,
        MetricsJmxExporter.class,
        MetricAnnotationConfiguration.class,
})
public class DefaultMetricsConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultMetricsConfiguration.class);

    @Bean
    public MetricRegistry getMetrics() {
        return new MetricRegistry();
    }

    @Bean
    public HealthCheckRegistry getHealthCheckRegistry() {
        return new HealthCheckRegistry();
    }
}
