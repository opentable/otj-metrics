package com.opentable.metrics.graphite;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sun.management.OperatingSystemMXBean;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.lifecycle.LifecycleStage;
import com.opentable.lifecycle.guice.OnStage;
import com.opentable.serverinfo.ServerInfo;

@Singleton
public class GraphiteReporter {
    @VisibleForTesting
    static Function<String, String> getenv = System::getenv;

    private final GraphiteConfig config;
    private final MetricRegistry metricRegistry;

    private String applicationName;

    private static final Logger LOG = LoggerFactory.getLogger(GraphiteReporter.class);
    private String graphiteHost = null;
    private int graphitePort = -1;
    private int reportingPeriodInSeconds = -1;

    @Inject
    public GraphiteReporter(GraphiteConfig config, MetricRegistry metricRegistry) {
        this.config = config;
        this.metricRegistry = metricRegistry;
    }

    @OnStage(LifecycleStage.START)
    public void start() {
        applicationName = (String) ServerInfo.get(ServerInfo.SERVER_TYPE);

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

    @VisibleForTesting
    String getPrefix() {
        final Environment env = getEnvironment();
        final String name = env.flavor == null ? applicationName : applicationName + "-" + env.flavor;
        final String instance = "instance-" + getInstanceNo();
        return String.join(".", Arrays.asList("app_metrics", name, env.type, env.location, instance));
    }

    @VisibleForTesting
    static Environment getEnvironment() {
        final String env = getenv.apply("OT_ENV_WHOLE");
        return env == null ? Environment.unknown : Environment.parse(env);
    }

    private String getInstanceNo() {
        final String i = getenv.apply("INSTANCE_NO");
        return i == null ? "unknown" : i;
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

    @VisibleForTesting
    static class Environment {
        static final Environment unknown = new Environment("unknown", "unknown", null);
        final String type;
        final String location;
        @Nullable
        final String flavor;
        static Environment parse(@Nonnull final String env) {
            final String[] split1 = env.split("-");
            if (split1.length < 2) {
                throw new IllegalArgumentException(
                        String.format("cannot parse type and location in environment %s", env));
            }
            final String type = split1[0];
            final String[] split2 = split1[1].split("\\.");
            final String location = split2[0];
            final String flavor = split2.length == 1 ? null : split2[1];
            return new Environment(type, location, flavor);
        }
        private Environment(final String type, final String location, final String flavor) {
            this.type = type;
            this.location = location;
            this.flavor = flavor;
        }
    }
}
