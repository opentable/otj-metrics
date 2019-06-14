package com.opentable.metrics.jaxrs;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.prometheus.PrometheusConfiguration;

@Configuration
@Import({
        PrometheusJAXRSResource.class,
        PrometheusConfiguration.class
})
public class PrometheusJAXRSConfiguration {
}
