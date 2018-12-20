package com.opentable.metrics.actuate.health;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link HealthIndicator} beans in the {@link HealthCheckRegistry}.
 * In other words takes Spring Actuator HealthIndicators and converts to DropWizard Healthchecks
 * Dmitry: Is there any weirdisms about effectively having duplicate Health checks (eg both DropWizard and Actuator?)
 * Couldn't that cause an infinite loop in addition to duplicate checks?
 */
@Configuration
public class HealthIndicatorCodahaleConfiguration {

    final HealthCheckRegistry registry;
    final Map<String, HealthIndicator> checks;

    @Inject
    // Again, I feel dumb. I see how you could inject HealthIndicator, and btw shouldn't this be Optional<Map>, but
    // But where does the key come from
    public HealthIndicatorCodahaleConfiguration(HealthCheckRegistry registry,   Map<String, HealthIndicator> checks) {
        this.registry = registry;
        this.checks = checks;
    }

    @PostConstruct
    void postConstruct() {
        checks.forEach((k, v) -> {
            if (!(v instanceof CodahaleHealthIndicatorConfiguration.Adapter)) {
                registry.register(k, new Adapter(v));
            }
        });
    }

    @PreDestroy
    void preDestroy() {
        checks.keySet().forEach(registry::unregister);
    }

    /**
     * Adaptor for Actuator => DropWizard
     */
    public static class Adapter extends HealthCheck {

        private final HealthIndicator indicator;

        public Adapter(HealthIndicator indicator) {
            this.indicator = indicator;
        }

        @Override
        protected Result check() {
            final Health res = indicator.health();
            if (Status.UP.equals(res.getStatus())) {
                return  HealthCheck.Result.healthy(res.toString());
            }
            return  HealthCheck.Result.unhealthy(res.toString());
        }
    }
}
