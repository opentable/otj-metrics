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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.health.HealthCheckRegistry;

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
    private final Set<String> registeredMetrics = ConcurrentHashMap.newKeySet();

    /**
     * Create a metric registry to register metrics on
     * @return the metric registry
     */
    @Bean
    public MetricRegistry getMetricRegistry() {
        return new MetricRegistry();
    }

    /**
     * Create a health check registry to register health checks with
     * @return health check registry
     */
    @Bean
    public HealthCheckRegistry getHealthCheckRegistry() {
        return new HealthCheckRegistry();
    }

    /**
     * Create a metric set builder. This is a prototype bean, a new one will be create whenever it is injetced.
     *
     * @param registry the registry to register this metric set with when ready
     * @return a new metric set builder
     */
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Bean
    public MetricSetBuilder metricSetBuilder(MetricRegistry registry) {
        final MetricSetBuilder builder = new MetricSetBuilder(registry);
        builders.add(builder);
        return builder;
    }

    /**
     * Create an application event listener that listens to every application event.
     * If there is a metric set builder that is waiting for an event to be registered,
     * we'll build and register it when that event happens.
     *
     * @param registry metric registry to register the metrics on
     * @return the event listener
     */
    @Bean
    public ApplicationListener<ApplicationEvent> metricRegistrar(MetricRegistry registry) {
        return event -> {
            List<MetricSet> metrics = findEventedMetricSets()
                .filter(m -> m.getEventClass().isInstance(event))
                .collect(Collectors.toList());
            if (!metrics.isEmpty()) {
                LOG.debug("Registering metrics on event {}: {}", event, metrics);
                metrics.forEach(registry::registerAll);
                metrics.forEach(m -> registeredMetrics.addAll(m.getMetrics().keySet()));
            }
        };
    }

    /**
     * Unregister all metrics on destruction
     */
    @PreDestroy
    public void metricUnregister() {
        LOG.debug("Unregistering metrics on close: {}", registeredMetrics);
        getMetricRegistry().removeMatching((name, metric) -> registeredMetrics.contains(name));
    }

    private Stream<BuiltMetricSet> findEventedMetricSets() {
        return builders.stream()
                .map(MetricSetBuilder::build)
                .map(BuiltMetricSet.class::cast)
                .filter(m -> m.getEventClass() != null);
    }
}
