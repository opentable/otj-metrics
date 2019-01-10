package com.opentable.metrics.actuate.health;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;

import org.springframework.boot.actuate.autoconfigure.health.CompositeHealthIndicatorConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Presents {@link HealthCheck} beans as a composite {@link HealthIndicator}.
 *
 * You can enable/disable export using property:
 *   <ul>
 *     <li>{@code management.health.drop-wizard.enabled}</li>
 *   </ul>
 * <br>
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-endpoints.html">Documentation</a>
 */
@Configuration
@ConditionalOnEnabledHealthIndicator("drop-wizard")
public class CodahaleHealthIndicatorConfiguration extends  CompositeHealthIndicatorConfiguration<CodahaleHealthIndicatorConfiguration.Adapter, HealthCheck> {

    private final Map<String, HealthCheck> dropWizardChecks;

    /**
     *Dependencies:
     * @param checks Map of {@link HealthCheck} beans. Bean names as the key (Spring collections autowiring feature)
     */
    @Inject
    CodahaleHealthIndicatorConfiguration(final Optional< Map<String, HealthCheck>> checks) {
        dropWizardChecks = checks.orElse(Collections.emptyMap());
    }

    /**
     * Creates {@link org.springframework.boot.actuate.health.CompositeHealthIndicator} bean which represents state of
     * all injected {@link HealthCheck} beans. This bean is created conditionally, if there are no other  "dropWizardHealthIndicator"
     * defined in application context.
     *
     * @return HealthIndicator
     */
    @ConditionalOnMissingBean
    @Bean
    public HealthIndicator dropWizardHealthIndicator() {
        return createHealthIndicator(this.dropWizardChecks);
    }

    /**
     * Delegating adapter {@link HealthCheck} -> {@link HealthIndicator}
     */
    public static class Adapter implements HealthIndicator {

        private final HealthCheck healthCheck;

        public Adapter(HealthCheck healthCheck) {
            this.healthCheck = healthCheck;
        }

        @Override
        public Health health() {
            final Result res = healthCheck.execute();
            final Builder builder = Health
                .status(res.isHealthy() ? Status.UP : Status.DOWN)
                .withDetail("message", res.getMessage());
            if (res.getError() != null) {
                builder.withException(res.getError());
            }
            return builder.build();
        }
    }

}
