package com.opentable.metrics.actuate.micrometer;

import java.text.Normalizer;
import java.util.regex.Pattern;

import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

@Configuration
public class OtMicrometerToDropWizardExportConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OtMicrometerToDropWizardExportConfiguration.class);
    private static final Pattern blacklistedChars = Pattern.compile("[{}(),=\\[\\]/]");

    private DropwizardConfig dropwizardConfig() {
        return new DropwizardConfig() {
            @Override
            @NonNull
            public String prefix() {
                return "management.metrics.dw-new";
            }

            @Override
            @Nullable
            public String get(String s) {
                return null;
            }
        };
    }


    private HierarchicalNameMapper hierarchicalNameMapper() {
        return (id, convention) -> {
            StringBuilder tags = new StringBuilder();
            if (!"true".equals(id.getTag("absolute"))) {
                for (Tag tag : id.getTags()) {
                    tags.append(("." + /*convention.tagKey(tag.getKey()) + "."  + */ convention.tagValue(tag.getValue()))
                        .replace(" ", "_"));
                }
                final String res = "v2." + id.getConventionName(convention) + tags;
                log.trace("Hierarchical mapping: {} -> {}", id, res);
                return res;
            }
            return id.getName();
        };
    }

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

    @Bean
    public MeterRegistry newDropWizardMeterRegistry(MetricRegistry registry, Clock clock) {
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
        return res;
    }


}
