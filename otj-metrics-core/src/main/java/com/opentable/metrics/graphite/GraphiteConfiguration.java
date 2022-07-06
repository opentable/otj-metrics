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
package com.opentable.metrics.graphite;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.google.common.net.HostAndPort;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.K8sInfo;
import com.opentable.service.ServiceInfo;
import com.opentable.spring.ConversionServiceConfiguration;

@Configuration
@Import({
        /**
         * {@link ConversionServiceConfiguration} needed for {@link java.time.Duration} config value.
         */
        ConversionServiceConfiguration.class,
        K8sInfo.class
})
@PropertySource("classpath:/com/opentable/metrics/micrometer.properties")
/**
 * Spring-ey wrapper for Dropwizard Metrics Graphite reporter.
 *
 * <p>
 * Constructs metric prefix based on {@link ServiceInfo}, {@link AppInfo}, and {@link EnvInfo}. Performs automatic
 * periodic Graphite failure detection and connection recycling.  Provides {@link GraphiteSender} implementation
 * that bravely remains connected in face of danger.
 */
public class GraphiteConfiguration {
    static final String PREFIX = "metrics.graphite.";

    private static final Logger LOG = LoggerFactory.getLogger(GraphiteConfiguration.class);
    private static final int DEFAULT_PORT = 2003;


    @Value("${ot.graphite.prefix:app_metrics}")
    private String graphitePrefix = "app_metrics"; //NOPMD

    // Original Java standards for setting host and port
    @Value("${ot.graphite.graphite-host:#{null}}")
    private String graphiteHost;

    @Value("${ot.graphite.graphite-port:2003}")
    private int graphitePort;

    // 12 factor standard of setting host and port
    @Value("${METRICS_GRAPHITE_URL:#{null}}")
    private String twelveFactorURL;

    @Value("${ot.graphite.reporting-period:PT10s}")
    private Duration reportingPeriod;

    @Value("${ot.graphite.reporting.include.flavors:#{true}}")
    private boolean showFlavorInPrefix = true; //NOPMD

    @Value("ot.graphite.reporting.include.cluster.type:#{null}}")
    private String clusterNameType;

    @Value("${metrics.micrometer.enabled:#{false}}")
    private boolean micrometerEnabled;

    @Value("${management.metrics.export.dw-new.prefix:micrometer}")
    private final String MicrometerMetricsPrefix = "micrometer"; //NOPMD

    private MetricRegistry metricRegistry;
    private MetricSet registeredMetrics;

    public Duration getReportingPeriod() {
        return reportingPeriod;
    }

    @Bean
    MetricFilter dropWizardMetricFilter() {
        return (s, metric) -> {
            if (micrometerEnabled) {

                if (s.contains(MicrometerMetricsPrefix)) {
                    return true;
                }

                for (DWMetricsToFilter metricToFilter: DWMetricsToFilter.values()) {
                    if (s.contains(metricToFilter.getMetricPathId())) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    @Bean
    public ScheduledReporter graphiteReporter(Optional<GraphiteSender> sender,
                                              MetricRegistry metricRegistry, ServiceInfo serviceInfo,
                                              AppInfo appInfo,  K8sInfo k8sInfo,
                                              Environment environment) {
        if (!sender.isPresent()) {
            LOG.warn("No sender to report to, skipping reporter initialization");
            return null;
        }
        final String prefix = getPrefix(graphitePrefix, serviceInfo, appInfo, k8sInfo,
                showFlavorInPrefix, ClusterNameType.fromParameterName(clusterNameType));
        if (prefix == null) {
            LOG.warn("insufficient information to construct metric prefix; skipping reporter initialization");
            return null;
        }

        final Optional<HostAndPort> hostAndPort = getHostPort();
        hostAndPort.ifPresent(hp -> {
            LOG.info("initializing: host {}, port {}, prefix {}, refresh period {}", hp.getHost(), hp.getPortOrDefault(DEFAULT_PORT), prefix, reportingPeriod);
        });

        ScheduledReporter reporter;
        reporter = OtGraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(prefix)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(dropWizardMetricFilter())
                .build(sender.get());

        reporter.start(reportingPeriod.toMillis(), TimeUnit.MILLISECONDS);
        return reporter;
    }

    @Bean
    public GraphiteSender graphiteSender(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        final Optional<HostAndPort> hostAndPortOptional = getHostPort();
        if (!hostAndPortOptional.isPresent()) {
            LOG.info("no graphite host; skipping sender initialization");
            return null;
        }
        final HostAndPort hostAndPort = hostAndPortOptional.get();
        @SuppressWarnings("PMD.CloseResource")
        final Graphite graphite = new Graphite(hostAndPort.getHost(), hostAndPort.getPortOrDefault(DEFAULT_PORT));
        GraphiteSenderWrapper result = new GraphiteSenderWrapper(hostAndPort.getHost(), hostAndPort.getPortOrDefault(DEFAULT_PORT), graphite);
        registeredMetrics = MetricSets.combineAndPrefix(PREFIX, result);
        metricRegistry.registerAll(registeredMetrics);
        return result;
    }

    @PreDestroy
    void close() {
        if (getHostPort().isPresent()) {
            MetricSets.removeAll(metricRegistry, registeredMetrics);
        }
    }

    private String getPrefix(String graphitePrefix, ServiceInfo serviceInfo, AppInfo appInfo, K8sInfo k8sInfo,
                            boolean includeFlavorInPrefix, ClusterNameType clusterNameType ) {
        final String applicationName = serviceInfo.getName();
        final EnvInfo env = appInfo.getEnvInfo();
        final Integer i = appInfo.getInstanceNumber();
        if (env == null || env.getType() == null || env.getLocation() == null || i == null) {
            return null;
        }
        final String name = env.getFlavor() == null || (!includeFlavorInPrefix) ? applicationName : applicationName + "-" + env.getFlavor();
        final String instance = "instance-" + i;

        // On Kubernetes include the cluster name
        if (k8sInfo.isKubernetes() && k8sInfo.getClusterName().isPresent()) {
            switch(clusterNameType) {
                case SEPARATE: {
                    return String.join(".", Arrays.asList(graphitePrefix, k8sInfo.getClusterName().get(), name,
                            env.getType(), env.getLocation(), instance));
                }
                case PART_OF_INSTANCE: {
                    return String.join(".", Arrays.asList(graphitePrefix, name,
                            env.getType(), env.getLocation(), k8sInfo.getClusterName().get() + "-"+ instance));
                }
                case NONE: {
                    break; // effectively skips
                }
                default: {
                    throw new IllegalArgumentException("Can't understand ClusterNameType " + clusterNameType);
                }
            }
        }
        // Cluster name is not included
        return String.join(".", Arrays.asList(graphitePrefix, name, env.getType(), env.getLocation(), instance));
    }

    private enum ClusterNameType {
        PART_OF_INSTANCE("instance"),
        SEPARATE("separate"),
        NONE("none")
        ;
        private final String parameterName;
        ClusterNameType(String parameterName) {
            this.parameterName = parameterName;
        }

        public String getParameterName() {
            return parameterName;
        }

        public static ClusterNameType fromParameterName(String parameterName) {
            return Arrays.stream(ClusterNameType.values())
                    .filter(t -> t.getParameterName().equalsIgnoreCase(parameterName))
                    .findFirst().orElse(ClusterNameType.PART_OF_INSTANCE);
        }
    }

    private Optional<HostAndPort> getHostPort() {
        if (StringUtils.isNotBlank(graphiteHost)) {
            return Optional.of(HostAndPort.fromParts(graphiteHost, graphitePort));
        }
        if (StringUtils.isNotBlank(twelveFactorURL)) {
            return Optional.of(HostAndPort.fromString(twelveFactorURL));
        }
        return Optional.empty();
    }

}
