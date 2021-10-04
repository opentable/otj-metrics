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
package com.opentable.metrics.autoconfigure;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimplePropertiesConfigAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.opentable.metrics.DefaultMetricsConfiguration;
import com.opentable.metrics.MetricAnnotationConfiguration;
import com.opentable.metrics.mvc.HealthHttpMVCConfiguration;
import com.opentable.metrics.reactive.HealthHttpReactiveConfiguration;

@Configuration
public class OtjSpringBootMetricsAutoConfiguration {

    @Configuration
    @Profile({"default", "development", "test", "local"})
    @Import({MetricAnnotationConfiguration.class})
    static class Local {

        @Bean
        @ConditionalOnMissingBean
        public MetricRegistry getMetricRegistry() {
            return new MetricRegistry();
        }

        @Bean
        @ConditionalOnMissingBean
        public HealthCheckRegistry getHealthCheckRegistry() {
            return new HealthCheckRegistry();
        }

        @Bean
        @ConditionalOnMissingBean
        public HealthCheck fakeHealthCheck() {
            return new HealthCheck() {
                @Override
                protected Result check() throws Exception {
                    return Result.healthy();
                }
            };
        }
    }

    @Configuration
    @Profile({"metric-test", "ci-rs", "pp-rs", "ci-sf", "pp-sf", "prod-sc", "prod-ln"})
    @EnableConfigurationProperties(SimpleProperties.class)
    @Import({
            DefaultMetricsConfiguration.class,
    })
    static class Defaut {

        @Bean
        public SimpleMeterRegistry simpleMeterRegistry(SimpleConfig config, Clock clock) {
            return new SimpleMeterRegistry(config, clock);
        }

        @Bean
        @ConditionalOnMissingBean
        public SimpleConfig simpleConfig(SimpleProperties simpleProperties) {
            return new SimplePropertiesConfigAdapter(simpleProperties);
        }

        @Bean
        public MeterFilter defaultIgnoreKafkaNode() {
            return MeterFilter.denyNameStartsWith("kafka.consumer.node");
        }

        @Bean
        public MeterFilter defaultIgnoreKafkaCoordinator() {
            return MeterFilter.denyNameStartsWith("kafka.consumer.coordinator");
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HealthHttpMVCConfiguration.class)
    @Profile({"metric-test", "ci-rs", "pp-rs", "ci-sf", "pp-sf", "prod-sc", "prod-ln"})
    @Import({
            HealthHttpMVCConfiguration.class,
    })
    static class DefaultMvc {
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(HealthHttpReactiveConfiguration.class)
    @Profile({"metric-test", "ci-rs", "pp-rs", "ci-sf", "pp-sf", "prod-sc", "prod-ln"})
    @Import({
            HealthHttpReactiveConfiguration.class
    })
    static class DefaultReactive {
    }
}
