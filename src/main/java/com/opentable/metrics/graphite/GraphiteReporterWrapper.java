package com.opentable.metrics.graphite;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mogwee.executors.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.opentable.concurrent.OTExecutors;
import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.ServiceInfo;

@Component
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

    private String prefix;
    private ScheduledExecutorService exec;
    private Graphite graphite;
    private GraphiteReporter reporter;

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
        prefix = getPrefix();
        if (prefix == null) {
            LOG.warn("insufficient information to construct metric prefix; skipping initialization");
            return;
        }

        init();
        exec = Executors.newSingleThreadScheduledExecutor("graphite-checker");
        final Duration checkPeriod = reportingPeriod.multipliedBy(3);
        exec.scheduleAtFixedRate(
                this::checkGraphite, checkPeriod.toMillis(), checkPeriod.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("kicked off thread to check on graphite connection with period {}", checkPeriod);
    }

    @PreDestroy
    public void preDestroy() throws InterruptedException {
        if (prefix != null) {
            OTExecutors.shutdownAndAwaitTermination(exec, Duration.ofSeconds(2));
            exec = null;
            shutdown();
        }
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

    private void init() {
        LOG.info("initializing: host {}, port {}, prefix {}, refresh period {}", host, port, prefix, reportingPeriod);

        Preconditions.checkState(graphite == null);
        Preconditions.checkState(reporter == null);

        graphite = new Graphite(new InetSocketAddress(host, port));
        reporter = GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(prefix)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(reportingPeriod.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void shutdown() {
        Preconditions.checkNotNull(reporter);
        Preconditions.checkNotNull(graphite);
        LOG.info("stopping reporter");
        // Reporter will close graphite.
        reporter.stop();
        reporter = null;
        graphite = null;
    }

    private void checkGraphite() {
        if (!graphite.isConnected() || graphite.getFailures() > 0) {
            LOG.warn("bad graphite state; recycling; connected {}, failures {}",
                    graphite.isConnected(), graphite.getFailures());
            shutdown();
            init();
        }
    }
}
