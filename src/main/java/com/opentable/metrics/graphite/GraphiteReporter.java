package com.opentable.metrics.graphite;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.opentable.lifecycle.LifecycleStage;
import com.opentable.lifecycle.guice.OnStage;
import com.opentable.server.PortNumberProvider;
import com.opentable.serverinfo.ServerInfo;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

@Singleton
public class GraphiteReporter {

    private final GraphiteConfig config;
    private final PortNumberProvider portNumberProvider;
    private final MetricRegistry metricRegistry;

    private String applicationName;

    private int port;

    private static final Logger LOG = LoggerFactory.getLogger(GraphiteReporter.class);
    private String graphiteHost = null;
    private int graphitePort = -1;
    private int reportingPeriodInSeconds = -1;


    @Inject
    public GraphiteReporter(GraphiteConfig config, PortNumberProvider portNumberProvider, MetricRegistry metricRegistry) {
        this.config = config;
        this.portNumberProvider = portNumberProvider;
        this.metricRegistry = metricRegistry;
    }

    @OnStage(LifecycleStage.START)
    public void start() {
        applicationName = (String) ServerInfo.get(ServerInfo.SERVER_TYPE);

        try {
            port = portNumberProvider.getPort();
        } catch (IOException e) {
            LOG.error("Can't find port", e);
            return;
        }

        graphiteHost = config.getGraphiteHost();
        this.graphitePort = config.getGraphitePort();
        this.reportingPeriodInSeconds = config.getReportingPeriodInSeconds();

        final String prefix = getPrefix();
        LOG.info("Initializing Graphite metrics reporter with host {}, port {}, prefix {}, refresh period {} seconds", graphiteHost, graphitePort, prefix, reportingPeriodInSeconds);

        if (Strings.isNullOrEmpty(graphiteHost)) {
            LOG.info("Skipping Graphite metrics reporter initialization");
            return;
        }

        cpuSetupInitial(metricRegistry);

        final Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, graphitePort));

        com.codahale.metrics.graphite.GraphiteReporter reporter = com.codahale.metrics.graphite.GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(prefix)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(reportingPeriodInSeconds, TimeUnit.SECONDS);
    }

    private String getPrefix() {
        return String.format("app_metrics.%s.%s.%s.%s-%s", applicationName, getEnvironment(), getRegion(), getHost().replaceAll("\\.", "-"), port);
    }

    private String getEnvironment() {
        final String[] strings = getProfile().split("-");
        if (strings.length < 1) {
            return "default";
        }

        return strings[0];
    }

    private String getRegion() {
        final String[] strings = getProfile().split("-");
        if (strings.length < 2) {
            return "default";
        }

        return strings[1];
    }

    private String getProfile() {
        final String ot_env = System.getenv("OT_ENV");
        if (ot_env == null) {
            return "default";
        }
        return ot_env;
    }

    private String getHost() {
        if (System.getenv("CONTAINER_HOST") != null) {
            return System.getenv("CONTAINER_HOST");
        } else {
            try {
                return InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                LOG.error("Error resolving application host", e);
                throw new RuntimeException(e);
            }
        }
    }

    final int cores = Runtime.getRuntime().availableProcessors();
    OperatingSystemMXBean osMXBean;
    private void cpuSetupInitial(MetricRegistry metricRegistry) {
        osMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        //Add CPU Gauge
        metricRegistry.register(MetricRegistry.name("jvm-cpu"), (Gauge<Double>) this::getProcessCpuLoad);
    }

    public double getProcessCpuLoad() {
        if(osMXBean == null) {
            return Double.NaN;
        }
        final double processCpuLoad = osMXBean.getProcessCpuLoad();

        //Like Top: Percentage with 1 decimal point
        return ((int)(processCpuLoad * 1000 * cores) / 10.0);
    }
}
