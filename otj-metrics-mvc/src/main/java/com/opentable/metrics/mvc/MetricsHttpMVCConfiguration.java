package com.opentable.metrics.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.http.MetricsHttpCoreConfiguration;

@Configuration
@Import({
    MetricsHttpCoreConfiguration.class,
    MetricsHttpEndpoint.class,
})
public class MetricsHttpMVCConfiguration {

}

