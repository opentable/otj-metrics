package com.opentable.metrics.micrometer;

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

    @Value("${management.metrics.export.dw-new.prefix:micrometer}")
    private final String MicrometerMetricsPrefix = "micrometer"; // NOPMD

    /**
     Instead of creating an instance of {@link io.micrometer.graphite.GraphiteMeterRegistry}
     We are creating an instance of super class {@link DropwizardMeterRegistry}
     so that we can insert the pre-configured MetricRegistry also used in the previous DropWizard Config
     @return MeterRegistry
     */
    @Bean
    public MeterRegistry graphite(MetricRegistry metricRegistry, Clock clock) {
        GraphiteConfig graphiteConfig = new GraphiteConfig() {

            /**
             accept the rest of the defaults by @return null.
             Configuration for host, port and reportingPeriod are injected via
             {@link com.opentable.metrics.graphite.GraphiteConfiguration}
             */
            @Override
            @Nullable
            public String get(String k) {
                return null;
            }

            /**
            Disable tags makes concise metric names:
            eg process.cpu.usage -> processCpuUsage
             */
            @Override
            public boolean graphiteTagsEnabled() {
                return false;
            }

        };

        return new DropwizardMeterRegistry(
                graphiteConfig,
                metricRegistry,
                new CustomNameMapper(MicrometerMetricsPrefix),
                clock
        ) {
            /**
            If Gauge.value() returns null, @return null
            This is also the default behavior in {@link io.micrometer.graphite.GraphiteMeterRegistry} class
             */
            @Override
            @Nullable
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
