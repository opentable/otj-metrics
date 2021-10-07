package com.opentable.metrics.micrometer;

import java.time.Duration;

import com.codahale.metrics.MetricRegistry;

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

    private static final String MicrometerMetricsPrefix = "micrometer";

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

        return new DropwizardMeterRegistry(
                graphiteConfig,
                metricRegistry,
                new CustomNameMapper(MicrometerMetricsPrefix),
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
