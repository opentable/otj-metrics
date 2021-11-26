package com.opentable.metrics.micrometer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.config.MeterFilter;

@Configuration
public class WebMvcMetricsCustomNameConfiguration {

    private static final String metricPrefix = "http.server.requests";
    private static final String[] tagsToIgnore = {"outcome"};

    @Bean
    public MeterFilter ignoreTagsInJvmMetrics() {
        return MeterFilterUtils.ignoreTags(metricPrefix, tagsToIgnore);
    }
}
