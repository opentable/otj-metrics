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
package com.opentable.metrics.actuate.micrometer;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;

/**
 * Configures  {@link DropwizardMeterRegistry} with custom {@link HierarchicalNameMapper} and
 * {@link NamingConvention} to report micrometer metrics in the DropWizard infrastructure.
 * <br><br>
 * Configuration properties:
 * <ul>
 *   <li>{@code management.metrics.export.dw-new.enabled} - To enable/disable export. Disabled by default.</li>
 *   <li>{@code management.metrics.export.dw-new.prefix} - Prefix to add to the reported metrics. Default is "v2".</li>
 * </ul>
 *<br>
 * All user supplied {@link MeterFilter} beans are wired up to the resulting bean.
 *<br>
 * NOTE: {@code MeterFilter.denyNameStartsWith("jvm")} automatically added to the filter list.
 *
 */
@Configuration
@Import(OtMicrometerConfig.class)
@ConditionalOnProperty(prefix = OtMicrometerConfig.CONFIGURATION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class OtMicrometerToDropWizardExportConfiguration {


    private static final Logger log = LoggerFactory.getLogger(OtMicrometerToDropWizardExportConfiguration.class);
    private static final Pattern blacklistedChars = Pattern.compile("[{}(),=\\[\\]/]");

    private final String dwMetricsPrefix;
    private final Environment env;

    @Inject
    public OtMicrometerToDropWizardExportConfiguration(OtMicrometerConfig otMicrometerConfig, Environment env) {
        dwMetricsPrefix = otMicrometerConfig.getPrefix();
        this.env = env;
    }

    /**
     *
     * Implementation of the {@link DropwizardConfig} interface, which reads from {@link Environment}
     *
     */
    private DropwizardConfig dropwizardConfig() {
        return new DropwizardConfig() {
            @Override
            @NonNull
            public String prefix() {
                return OtMicrometerConfig.CONFIGURATION_PREFIX;
            }

            @Override
            @Nullable
            public String get(@NonNull String s) {
                return env.getProperty(s);
            }
        };
    }

    /**
     *
     * Defines the mapping between a combination of name + dimensional tags and a hierarchical name:
     * <br>
     *   {@code <id>_<tag[0].value>_<tag[1].value>_..._<tag[n].value>}
     *
     */
    private HierarchicalNameMapper hierarchicalNameMapper() {
        return (id, convention) -> {
            final StringBuilder tags = new StringBuilder();
            if (!"true".equals(id.getTag("absolute"))) {
                for (Tag tag : id.getTags()) {
                    tags.append(("." + /*convention.tagKey(tag.getKey()) + "."  + */ convention.tagValue(tag.getValue()))
                        .replace(" ", "_"));
                }
                final String prefix = ((dwMetricsPrefix != null) && (!"".equals(dwMetricsPrefix))) ? dwMetricsPrefix + "." : "";
                final String res = prefix + id.getConventionName(convention) + tags;
                log.trace("Hierarchical mapping: {} -> {}", id, res);
                return res;
            }
            return id.getName();
        };
    }

    /**
     *
     * Provides naming convention suitable for Graphite
     *
     */
    private NamingConvention spring2xNamingConvention() {
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
     * Adds configuration property management.metrics.ignore-tags to be able
     * remove tags from configuration.
     */
    @Bean
    public MeterFilter ignoreTagsMeterFilter(
        @Value("${management.metrics.ignore-tags:}") final String[] ignoreTags
    ) {
        return MeterFilter.ignoreTags(ignoreTags);
    }

    @Bean
    public MeterRegistry newDropWizardMeterRegistry(MetricRegistry registry, Clock clock, Optional<List<MeterFilter>> filters) {
        DropwizardMeterRegistry res =  new DropwizardMeterRegistry(dropwizardConfig(), registry, hierarchicalNameMapper(), clock) {
            @Override
            @Nullable
            protected Double nullGaugeValue() {
                return null;
            }
        };
        res.config()
            .namingConvention(spring2xNamingConvention())
            .meterFilter(MeterFilter.denyNameStartsWith("jvm"));
        filters.ifPresent(l -> l.forEach(f -> res.config().meterFilter(f)));
        return res;
    }

}
