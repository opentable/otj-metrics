package com.opentable.metrics.actuate.health;

import java.util.Arrays;
import java.util.Map;

import javax.inject.Inject;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.actuate.autoconfigure.health.CompositeHealthIndicatorConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import io.micrometer.core.lang.NonNull;

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
     * @param checks Map of HealthCheck beans. Bean names as the key (Spring collections autowiring feature)
     */
    @Inject
    CodahaleHealthIndicatorConfiguration(final Map<String, HealthCheck> checks) {
        dropWizardChecks = checks;
    }

    @Bean
    @Conditional(MissingBeanCondition.class)
    public HealthIndicator dropWizardHealthIndicator() {
        return createHealthIndicator(this.dropWizardChecks);
    }

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

    static class MissingBeanCondition implements Condition {
        @Override
        public boolean matches(@NonNull ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
            final ConfigurableListableBeanFactory factory = context.getBeanFactory();
            if (factory == null) {
                return false;
            }
            return !Arrays.asList(factory.getBeanNamesForType(HealthIndicator.class))
                .contains("dropWizardHealthIndicator");
        }
    }

}
