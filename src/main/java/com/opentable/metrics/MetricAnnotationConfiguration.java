package com.opentable.metrics;

import com.codahale.metrics.MetricRegistry;
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
 * structure, class naming, and function naming.  Therefore, if you rearrange or refactor your code, your metric names
 * may implicitly be changed as well.
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
