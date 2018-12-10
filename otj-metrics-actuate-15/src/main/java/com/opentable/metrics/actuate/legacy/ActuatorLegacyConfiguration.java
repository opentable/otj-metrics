package com.opentable.metrics.actuate.legacy;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    WebMvcLegacyMetricsFilter.class
})
public class ActuatorLegacyConfiguration {
}
