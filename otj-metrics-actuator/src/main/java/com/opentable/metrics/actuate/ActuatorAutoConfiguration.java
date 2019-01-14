package com.opentable.metrics.actuate;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.actuate.health.CodahaleHealthIndicatorConfiguration;
import com.opentable.metrics.actuate.health.HealthIndicatorCodahaleConfiguration;
import com.opentable.metrics.actuate.micrometer.OtMicrometerToDropWizardExportConfiguration;

@Configuration
@Import({
    OtMicrometerToDropWizardExportConfiguration.class,
    CodahaleHealthIndicatorConfiguration.class,
    HealthIndicatorCodahaleConfiguration.class
})
public class ActuatorAutoConfiguration {
}
