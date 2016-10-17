package com.opentable.metrics.graphite;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.ServiceInfo;
import com.opentable.spring.ConversionServiceConfiguration;

@Configuration
@Import({
        /**
         * {@link ConversionServiceConfiguration} needed for {@link java.time.Duration} config value.
         */
        ConversionServiceConfiguration.class,
})
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

    @Value("${ot.graphite.graphite-host:#{null}}")
    private String host;

    @Value("${ot.graphite.graphite-port:2003}")
    private int port;

    @Value("${ot.graphite.reporting-period:PT10s}")
    private Duration reportingPeriod;

    private MetricRegistry metricRegistry;
    private MetricSet registeredMetrics;

    @Bean
    public GraphiteReporter graphiteReporter(GraphiteSender sender, MetricRegistry metricRegistry, ServiceInfo serviceInfo, AppInfo appInfo) {
        if (sender == null) {
            LOG.warn("No sender to report to, skipping reporter initialization");
        }
        String prefix = getPrefix(serviceInfo, appInfo);
        if (prefix == null) {
            LOG.warn("insufficient information to construct metric prefix; skipping reporter initialization");
            return null;
        }

        LOG.info("initializing: host {}, port {}, prefix {}, refresh period {}", host, port, prefix, reportingPeriod);

        GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(prefix)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(sender);
        reporter.start(reportingPeriod.toMillis(), TimeUnit.MILLISECONDS);
        return reporter;
    }

    @Bean
    public GraphiteSender graphiteSender(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        if (Strings.isNullOrEmpty(host)) {
            LOG.info("no graphite host; skipping sender initialization");
            return null;
        }

        GraphiteSenderWrapper result = new GraphiteSenderWrapper(new InetSocketAddress(host, port));
        registeredMetrics = MetricSets.combineAndPrefix(PREFIX, result);
        metricRegistry.registerAll(registeredMetrics);
        return result;
    }

    @PreDestroy
    void close() {
        MetricSets.removeAll(metricRegistry, registeredMetrics);
    }

    @VisibleForTesting
    static String getPrefix(ServiceInfo serviceInfo, AppInfo appInfo) {
        final String applicationName = serviceInfo.getName();
        final EnvInfo env = appInfo.getEnvInfo();
        final Integer i = appInfo.getInstanceNumber();
        if (env.getType() == null || env.getLocation() == null || i == null) {
            return null;
        }
        final String name = env.getFlavor() == null ? applicationName : applicationName + "-" + env.getFlavor();
        final String instance = "instance-" + i;
        return String.join(".", Arrays.asList("app_metrics", name, env.getType(), env.getLocation(), instance));
    }
}
