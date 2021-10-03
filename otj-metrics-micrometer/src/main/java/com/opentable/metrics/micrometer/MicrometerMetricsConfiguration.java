package com.opentable.metrics.micrometer;

import java.time.Duration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.web.jetty.JettyMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.web.servlet.WebMvcMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jetty.JettyConnectionMetrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.lang.Nullable;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;

import com.opentable.metrics.graphite.GraphiteConfiguration;
import com.opentable.service.AppInfo;
import com.opentable.service.K8sInfo;
import com.opentable.service.ServiceInfo;


@Configuration
@ConditionalOnProperty(prefix = "metrics.micrometer", name = "enabled", havingValue = "true")
@ImportAutoConfiguration({
        WebMvcMetricsAutoConfiguration.class,
        JettyMetricsAutoConfiguration.class
})
public class MicrometerMetricsConfiguration {

    private final GraphiteConfiguration dropwizardGraphiteConfiguration;

    @Value("${ot.graphite.prefix:app_metrics_micrometer}")
    private final String graphitePrefix = "app_metrics_micrometer"; //NOPMD

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
    public GraphiteMeterRegistry graphite() {
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

        };

        String prefix = dropwizardGraphiteConfiguration.getPrefix(
                graphitePrefix,
                serviceInfo,
                appInfo,
                k8sInfo,
                showFlavorInPrefix,
                GraphiteConfiguration.ClusterNameType.fromParameterName(clusterNameType)
        );

        return new GraphiteMeterRegistry(
                graphiteConfig,
                Clock.SYSTEM,
                new CustomNameMapper(prefix)
        );
    }

    @PostConstruct
    public void init() {
        GraphiteMeterRegistry graphiteMeterRegistry = graphite();

        //  Refer: org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration;
        new UptimeMetrics().bindTo(graphiteMeterRegistry);
        new ProcessorMetrics().bindTo(graphiteMeterRegistry);
        new FileDescriptorMetrics().bindTo(graphiteMeterRegistry);

        // Refer: org.springframework.boot.actuate.autoconfigure.metrics.web.jetty.JettyMetricsAutoConfiguration;
        new JvmGcMetrics().bindTo(graphiteMeterRegistry);
        new JvmMemoryMetrics().bindTo(graphiteMeterRegistry);
        new JvmThreadMetrics().bindTo(graphiteMeterRegistry);
        new ClassLoaderMetrics().bindTo(graphiteMeterRegistry);
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public JettyConnectionMetrics jettyConnectionMetrics(MeterRegistry registry) {
        return new JettyConnectionMetrics(registry);
    }
}
