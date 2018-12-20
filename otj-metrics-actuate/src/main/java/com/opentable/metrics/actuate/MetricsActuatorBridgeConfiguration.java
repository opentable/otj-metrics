package com.opentable.metrics.actuate;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.metrics.actuate.health.CodahaleHealthIndicatorConfiguration;
import com.opentable.metrics.actuate.health.HealthIndicatorCodahaleConfiguration;
import com.opentable.metrics.actuate.micrometer.OtMicrometerToDropWizardExportConfiguration;

@Configuration
@Import({
    OtMicrometerToDropWizardExportConfiguration.class,
    CodahaleHealthIndicatorConfiguration.class, // Adapt DropWizard HealthCheck to Actuator HealthIndicator
    HealthIndicatorCodahaleConfiguration.class // Adapt Actuator HealthIndicator to DropWizard Healthchexk
})
// Since this is just Metrics, might want to rename to MetricsActuatorConfiguration
// (It's not an AutoConfiguration class unless I'm missing something)
// I see you turn on AutoConfiguration in spring.factories? I really hate that. Is the scope limited here, or does it bleed? I assume it bleeds
// Is Autoconfiguration needed for the healthcheck stuff or just for micrometer? Try to separate if the latter.
public class MetricsActuatorBridgeConfiguration {
}
