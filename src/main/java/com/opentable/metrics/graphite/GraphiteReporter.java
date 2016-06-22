package com.opentable.metrics.graphite;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Named;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.opentable.service.ServiceInfo;

@Named
public class GraphiteReporter {
    @VisibleForTesting
    static Function<String, String> getenv = System::getenv;

    @Value("${ot.graphite.graphite-host:#{null}}")
    private String host;

    @Value("${ot.graphite.graphite-port:2003}")
    private int port;

    @Value("${ot.graphite.reporting-period:PT10s}")
    private Duration reportingPeriod;

    private final MetricRegistry metricRegistry;
    private final String applicationName;

    private static final Logger LOG = LoggerFactory.getLogger(GraphiteReporter.class);

    public GraphiteReporter(final ServiceInfo serviceInfo, final MetricRegistry metricRegistry) {
        applicationName = serviceInfo.getName();
        this.metricRegistry = metricRegistry;
    }

    @PostConstruct
    public void postConstruct() {
        final String prefix = getPrefix();
        LOG.info("Initializing Graphite metrics reporter with host {}, port {}, prefix {}, refresh period {}",
                host, port, prefix, reportingPeriod);

        if (Strings.isNullOrEmpty(host)) {
            LOG.info("Skipping Graphite metrics reporter initialization");
            return;
        }

        final Graphite graphite = new Graphite(new InetSocketAddress(host, port));

        com.codahale.metrics.graphite.GraphiteReporter reporter = com.codahale.metrics.graphite.GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(prefix)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(reportingPeriod.toMillis(), TimeUnit.MILLISECONDS);
    }

    // TODO When we can distinguish development from being deployed, we should probably blow up instead of setting
    // things to "unknown"...

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
