/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.metrics.micrometer;

import java.text.Normalizer;
import java.util.regex.Pattern;

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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.lang.Nullable;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;

@Configuration
@ConditionalOnProperty(prefix = "metrics.micrometer", name = "enabled", havingValue = "true")
@ImportAutoConfiguration({
        SystemMetricsAutoConfiguration.class,
        JvmMetricsAutoConfiguration.class,
        WebMvcMetricsCustomNameConfiguration.class,
        WebMvcMetricsAutoConfiguration.class,
        JettyMetricsAutoConfiguration.class,
        TimedMetricsCustomNameConfiguration.class,
        MetricsAutoConfiguration.class
})
public class MicrometerMetricsConfiguration {

    private static final Pattern blacklistedChars = Pattern.compile("[{}(),=\\[\\]/]");

    @Value("${management.metrics.export.dw-new.prefix:micrometer}")
    private final String MicrometerMetricsPrefix = "micrometer"; // NOPMD


    private NamingConvention graphiteNamingConvention() {
        return new NamingConvention() {

            @Override
            public String name(String name, Meter.Type type, String baseUnit) {
                return format(name);
            }

            @Override
            public String tagKey(String key) {
                return format(key);
            }

            @Override
            public String tagValue(String value) {
                return format(value);
            }

            /**
             * Github Issue: https://github.com/graphite-project/graphite-web/issues/243
             * Unicode is not OK. Some special chars are not OK.
             */
            private String format(String name) {
                String sanitized = Normalizer.normalize(name, Normalizer.Form.NFKD);
                sanitized = NamingConvention.camelCase.tagKey(sanitized);
                return blacklistedChars.matcher(sanitized).replaceAll("_");
            }

        };
    }

    /**
     * Instead of creating an instance of {@link io.micrometer.graphite.GraphiteMeterRegistry}
     * We are creating an instance of super class {@link DropwizardMeterRegistry}
     * so that we can insert the pre-configured MetricRegistry also used in the previous DropWizard Config
     *
     * @return MeterRegistry
     */
    @Bean
    public MeterRegistry graphite(MetricRegistry metricRegistry, Clock clock) {
        GraphiteConfig graphiteConfig = new GraphiteConfig() {

            /**
             accept the rest of the defaults by @return null.
             Configuration for host, port and reportingPeriod are injected via GraphiteConfiguration
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

        final DropwizardMeterRegistry res = new DropwizardMeterRegistry(
                graphiteConfig,
                metricRegistry,
                new CustomNameMapper(MicrometerMetricsPrefix),
                clock
        ) {
            /**
             * If Gauge.value() returns null, @return null
             * This is also the default behavior in {@link GraphiteMeterRegistry} class
             */
            @Override
            @Nullable
            protected Double nullGaugeValue() {
                return null;
            }
        };
        res.config().namingConvention(graphiteNamingConvention());
        return res;
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

}
