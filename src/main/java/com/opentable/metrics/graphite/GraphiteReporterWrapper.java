package com.opentable.metrics.graphite;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.inject.Named;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.ServiceInfo;

@Named
public class GraphiteReporterWrapper {
    @Value("${ot.graphite.graphite-host:#{null}}")
    private String host;

    @Value("${ot.graphite.graphite-port:2003}")
    private int port;

    @Value("${ot.graphite.reporting-period:PT10s}")
    private Duration reportingPeriod;

    private final String applicationName;
    private final AppInfo appInfo;
    private final MetricRegistry metricRegistry;

    private static final Logger LOG = LoggerFactory.getLogger(GraphiteReporterWrapper.class);

    public GraphiteReporterWrapper(
            final ServiceInfo serviceInfo,
            final AppInfo appInfo,
            final MetricRegistry metricRegistry) {
        applicationName = serviceInfo.getName();
        this.appInfo = appInfo;
        this.metricRegistry = metricRegistry;
    }

    @PostConstruct
    public void postConstruct() {
        if (Strings.isNullOrEmpty(host)) {
            LOG.info("no graphite host; skipping initialization");
            return;
        }

        final String prefix = getPrefix();
        if (prefix == null) {
            LOG.warn("insufficient information to construct metric prefix; skipping initialization");
            return;
        }

        LOG.info("initializing: host {}, port {}, prefix {}, refresh period {}", host, port, prefix, reportingPeriod);

        final Graphite graphite = new Graphite(new InetSocketAddress(host, port));

        final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(prefix)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(reportingPeriod.toMillis(), TimeUnit.MILLISECONDS);
    }

    @VisibleForTesting
    String getPrefix() {
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
