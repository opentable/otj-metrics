package com.opentable.metrics.reactive;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.prometheus.PrometheusConfiguration;

@Configuration
@Import({
        PrometheusReactiveResource.class,
        PrometheusConfiguration.class
})
public class PrometheusReactiveConfiguration {
}
