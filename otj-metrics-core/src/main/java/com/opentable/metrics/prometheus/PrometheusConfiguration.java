package com.opentable.metrics.prometheus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.prometheus.client.CollectorRegistry;

@Configuration
@Import({
        BridgeDropWizard.class,
        ExposeMetricsJAXRSResource.class,
})
public class PrometheusConfiguration {
    @Bean
    CollectorRegistry collectorRegistry() {
        return new CollectorRegistry();
    }
}
