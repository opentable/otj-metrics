package com.opentable.metrics.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.prometheus.PrometheusConfiguration;

@Configuration
@Import({
        PrometheusMVCResource.class,
        PrometheusConfiguration.class
})
public class PrometheusMVCConfiguration {
}
