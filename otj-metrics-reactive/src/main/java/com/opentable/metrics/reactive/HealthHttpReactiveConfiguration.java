package com.opentable.metrics.reactive;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.http.HealthController;

@Configuration
@Import({
        HealthController.class,
        HealthEndpoint.class,
})
public class HealthHttpReactiveConfiguration {
}
