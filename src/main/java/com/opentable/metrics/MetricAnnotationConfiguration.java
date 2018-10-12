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
package com.opentable.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurationSupport;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotationMetadata;

/**
 * We provide support for Ryan Tenney's Dropwizard metrics-spring integration, enabling developers to add
 * {@link com.codahale.metrics.annotation.Timed @Timed}, etc. annotations to their code!
 *
 * <p>
 * NB: The metric names automatically generated as a result of these annotations will be a function of the package
 * structure, class naming, and function naming (unless you use the, for example,
 * {@link Timed#name()}/{@link Timed#absolute()} parameters).  Therefore, if you rearrange or refactor your code, your
 * metric names may implicitly be changed as well.
 *
 * <p>
 * We do not use his {@link com.ryantenney.metrics.spring.config.annotation.EnableMetrics @EnableMetrics} annotation
 * since it ends up injecting a
 * {@link com.ryantenney.metrics.spring.config.annotation.DelegatingMetricsConfiguration DelegatingMetricsConfiguration}
 * bean, which injects its own beans for a {@link MetricRegistry} and a {@link HealthCheckRegistry}. Clearly, this
 * would be in conflict with our own injection.
 *
 * <p>
 * Therefore, we make our own subclass of {@link MetricsConfigurationSupport}, implemented by the above-mentioned
 * {@link com.ryantenney.metrics.spring.config.annotation.DelegatingMetricsConfiguration DelegatingMetricsConfiguration},
 * and use our own injected {@link MetricRegistry} and {@link HealthCheckRegistry}. In addition, and this is
 * potentially a hacky bit, we override {@link #setImportMetadata(AnnotationMetadata)} to avoid the default
 * implementation, which depends on you having used the
 * {@link com.ryantenney.metrics.spring.config.annotation.EnableMetrics @EnableMetrics} annotation. Granted, this
 * annotation, besides providing the configuration class, provides settings related <em>only</em> to AOP, which we do
 * not intend to use, so this should be OK. :]
 */
@Configuration
public class MetricAnnotationConfiguration extends MetricsConfigurationSupport {
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    /**
     * Create MetricAnnotationConfiguration based on {@link MetricsConfigurationSupport}, but with a custom metric and health check registry
     * @param metricRegistry our metric registry to use for metric annotations
     * @param healthCheckRegistry our health check registry to use for metric annotations
     */
    public MetricAnnotationConfiguration(
            final MetricRegistry metricRegistry,
            final HealthCheckRegistry healthCheckRegistry) {
        this.metricRegistry = metricRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @Override
    public void setImportMetadata(final AnnotationMetadata importMetadata) {
        // Do nothing; see comments above.
    }

    @Override
    protected MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    @Override
    protected HealthCheckRegistry getHealthCheckRegistry() {
        return healthCheckRegistry;
    }
}
