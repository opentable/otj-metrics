package com.opentable.metrics.actuate.health;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

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


    /**
     *Dependencies:
     *
     * @param registry HealthCheckRegistry bean
     * @param checks Map of HealthIndicator beans. Bean names as the key (Spring collections autowiring feature)
     */
    @Inject
    public HealthIndicatorCodahaleConfiguration(final HealthCheckRegistry registry,   final Optional<Map<String, HealthIndicator>> checks) {
        this.registry = registry;
        this.checks = checks.orElse(Collections.emptyMap());
    }

    /**
     * Registers {@link HealthIndicatorCodahaleConfiguration.Adapter} for each injected {@link HealthIndicator} bean
     * (except for {@link CodahaleHealthIndicatorConfiguration.Adapter}, to avoid circular link) in the {@link HealthCheckRegistry}
     */
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
     * Delegating adapter to convert {@link HealthIndicator} to {@link HealthCheck}
     */
    public static class Adapter extends HealthCheck {

        private final HealthIndicator indicator;

        Adapter(HealthIndicator indicator) {
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
