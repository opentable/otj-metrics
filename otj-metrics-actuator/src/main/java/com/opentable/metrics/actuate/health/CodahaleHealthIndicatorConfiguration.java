/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.metrics.actuate.health;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;

import org.springframework.boot.actuate.autoconfigure.health.CompositeHealthContributorConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthContributor;
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
public class CodahaleHealthIndicatorConfiguration extends CompositeHealthContributorConfiguration<CodahaleHealthIndicatorConfiguration.Adapter, HealthCheck> {

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
    public HealthContributor dropWizardHealthIndicator() {
        return this.dropWizardChecks.isEmpty() ? dummyContributor() : createContributor(this.dropWizardChecks);
    }

    private HealthContributor dummyContributor() {
        return (HealthIndicator) () -> Health.up().build();
    }

    /**
     * Delegating adapter to convert {@link HealthCheck} to {@link HealthIndicator}
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
