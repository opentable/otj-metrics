package com.opentable.metrics.graphite;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
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

/**
 * Spring-ey wrapper for Dropwizard Metrics Graphite reporter.
 *
 * <p>
 * Constructs metric prefix based on {@link ServiceInfo}, {@link AppInfo}, and {@link EnvInfo}. Performs automatic
 * periodic Graphite failure detection and connection recycling.
 */
@Component
public class GraphiteReporterWrapper implements MetricSet {
    private static final int CHECK_PERIOD_MULT = 2;

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

    private final Counter detectedConnectionFailures = new Counter();

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
        metricRegistry.registerAll(this::getGlobalMetrics);

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
        final Duration checkPeriod = reportingPeriod.multipliedBy(CHECK_PERIOD_MULT);
        exec.scheduleAtFixedRate(
                this::checkGraphite, checkPeriod.toMillis(), checkPeriod.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("kicked off thread to check on graphite connection with period {}", checkPeriod);
    }

    @PreDestroy
    public void preDestroy() throws InterruptedException {
        getGlobalMetrics().keySet().forEach(metricRegistry::remove);

        if (prefix != null) {
            OTExecutors.shutdownAndAwaitTermination(exec, Duration.ofSeconds(2));
            exec = null;
            shutdown();
        }
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return Collections.singletonMap("detected-connection-failures", detectedConnectionFailures);
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

    // Namespaced for global registry.
    private Map<String, Metric> getGlobalMetrics() {
        final Map<String, Metric> ret = new HashMap<>();
        getMetrics().forEach((name, metric) -> ret.put("metrics.graphite.reporter-wrapper." + name, metric));
        return Collections.unmodifiableMap(ret);
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
            detectedConnectionFailures.inc();
            shutdown();
            init();
        }
    }
}
