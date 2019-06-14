package com.opentable.metrics.prometheus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.samplebuilder.DefaultSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;

@Configuration
@Import({
        BridgeDropWizard.class,
})
public class PrometheusConfiguration {
    @Bean
    CollectorRegistry collectorRegistry() {
        return new CollectorRegistry(true);
    }

    @Bean
    SampleBuilder sampleBuilder() {
        return new DefaultSampleBuilder();
    }
}
