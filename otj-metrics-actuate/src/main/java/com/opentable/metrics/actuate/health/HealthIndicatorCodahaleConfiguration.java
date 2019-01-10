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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link HealthIndicator} beans in the {@link HealthCheckRegistry}.
 *
 */
@Configuration
@ConditionalOnProperty(prefix = "management.health.export-dw", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealthIndicatorCodahaleConfiguration {

    final HealthCheckRegistry registry;
    final Map<String, HealthIndicator> checks;

    @Inject
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
