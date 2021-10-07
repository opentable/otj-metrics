package com.opentable.metrics.micrometer;

import java.time.Duration;
import java.util.StringJoiner;

import com.codahale.metrics.MetricRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.JvmMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.web.jetty.JettyMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.web.servlet.WebMvcMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.lang.Nullable;
import io.micrometer.graphite.GraphiteConfig;

import com.opentable.metrics.graphite.GraphiteConfiguration;
import com.opentable.service.AppInfo;
import com.opentable.service.K8sInfo;
import com.opentable.service.ServiceInfo;


@Configuration
@ConditionalOnProperty(prefix = "metrics.micrometer", name = "enabled", havingValue = "true")
@ImportAutoConfiguration({
        SystemMetricsAutoConfiguration.class,
        JvmMetricsAutoConfiguration.class,
        WebMvcMetricsAutoConfiguration.class,
        JettyMetricsAutoConfiguration.class,
        MetricsAutoConfiguration.class
})
public class MicrometerMetricsConfiguration {

    private final GraphiteConfiguration dropwizardGraphiteConfiguration;

    private static final String MicrometerMetricsPrefix = "micrometer";

    @Value("${ot.graphite.prefix:app_metrics}")
    private final String graphitePrefix = "app_metrics"; //NOPMD

    private final ServiceInfo serviceInfo;

    private final AppInfo appInfo;

    private final K8sInfo k8sInfo;

    @Value("${ot.graphite.reporting.include.flavors:#{true}}")
    private final boolean showFlavorInPrefix = true; //NOPMD

    @Value("ot.graphite.reporting.include.cluster.type:#{null}}")
    private final String clusterNameType = null; //NOPMD

    public MicrometerMetricsConfiguration(
            GraphiteConfiguration dropwizardGraphiteConfiguration,
            ServiceInfo serviceInfo,
            AppInfo appInfo, K8sInfo k8sInfo) {
        this.dropwizardGraphiteConfiguration = dropwizardGraphiteConfiguration;
        this.serviceInfo = serviceInfo;
        this.appInfo = appInfo;
        this.k8sInfo = k8sInfo;
    }

    @Bean
    public MeterRegistry graphite(MetricRegistry metricRegistry, Clock clock) {
        GraphiteConfig graphiteConfig = new GraphiteConfig() {
            @Override
            public Duration step() {
                return Duration.ofSeconds(10);
            }

            @Override
            @Nullable
            public String get(String k) {
                return null;
            }

            @Override
            public boolean graphiteTagsEnabled() {
                return false;
            }

        };

        String prefix = dropwizardGraphiteConfiguration.getPrefix(
                graphitePrefix,
                serviceInfo,
                appInfo,
                k8sInfo,
                showFlavorInPrefix,
                GraphiteConfiguration.ClusterNameType.fromParameterName(clusterNameType)
        );

        StringJoiner sj = new StringJoiner(".");
        sj.add(prefix);
        sj.add(MicrometerMetricsPrefix);
        prefix = sj.toString();

        return new DropwizardMeterRegistry(
                graphiteConfig,
                metricRegistry,
                new CustomNameMapper(prefix),
                clock
        ) {
            @Override
            protected Double nullGaugeValue() {
                return null;
            }
        };
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

}
