package com.opentable.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

import com.opentable.metrics.MetricSetBuilder.BuiltMetricSet;
import com.opentable.metrics.graphite.GraphiteConfiguration;
import com.opentable.metrics.health.HealthConfiguration;

@Configuration
@Import({
        JettyServerMetricsConfiguration.class,
        JvmMetricsConfiguration.class,
        HealthConfiguration.class,
        GraphiteConfiguration.class,
        MetricsJmxExporter.class,
        MetricAnnotationConfiguration.class,
})
public class DefaultMetricsConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultMetricsConfiguration.class);

    private final List<MetricSetBuilder> builders = new ArrayList<>();
    private final Set<String> registeredMetrics = new ConcurrentHashSet<>();

    @Bean
    public MetricRegistry getMetricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public HealthCheckRegistry getHealthCheckRegistry() {
        return new HealthCheckRegistry();
    }

    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Bean
    public MetricSetBuilder metricSetBuilder(MetricRegistry registry) {
        final MetricSetBuilder builder = new MetricSetBuilder(registry);
        builders.add(builder);
        return builder;
    }

    @Bean
    public ApplicationListener<ApplicationEvent> metricRegistrar(MetricRegistry registry) {
        return event -> {
            List<MetricSet> metrics = findEventedMetricSets()
                .filter(m -> m.getEventClass().isInstance(event))
                .collect(Collectors.toList());
            LOG.info("Registering metrics on event {}: {}", event, metrics);
            metrics.forEach(registry::registerAll);
            metrics.forEach(m -> registeredMetrics.addAll(m.getMetrics().keySet()));
        };
    }

    @PreDestroy
    public void metricUnregister() {
        LOG.info("Unregistering metrics on close: {}", registeredMetrics);
        getMetricRegistry().removeMatching((name, metric) -> registeredMetrics.contains(name));
    }

    private Stream<BuiltMetricSet> findEventedMetricSets() {
        return builders.stream()
                .map(MetricSetBuilder::build)
                .map(BuiltMetricSet.class::cast)
                .filter(m -> m.getEventClass() != null);
    }
}
