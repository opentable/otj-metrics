package com.opentable.metrics.http;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        HealthController.class,
        HealthResource.class,
})
public class HealthHttpConfiguration {}
