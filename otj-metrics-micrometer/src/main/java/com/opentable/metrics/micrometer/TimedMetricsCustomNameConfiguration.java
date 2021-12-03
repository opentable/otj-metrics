package com.opentable.metrics.micrometer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.config.MeterFilter;

@Configuration
public class TimedMetricsCustomNameConfiguration {

    private static final String metricPrefix = "method.timed";
    private static final String[] tagsToIgnore = {"exception"};

    @Bean
    public MeterFilter ignoreTagsInTimedMetrics() {
        return MeterFilterUtils.ignoreTags(metricPrefix, tagsToIgnore);
    }
}
