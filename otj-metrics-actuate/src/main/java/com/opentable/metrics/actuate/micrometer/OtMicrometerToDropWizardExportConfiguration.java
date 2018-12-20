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

/**
 * Configures  {@link DropwizardMeterRegistry} with custom {@link HierarchicalNameMapper} and
 * {@link HierarchicalNameMapper} to report micrometer metrics in the DropWizard infrastructure
 */
@Configuration
public class OtMicrometerToDropWizardExportConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(OtMicrometerToDropWizardExportConfiguration.class);
    // Comment here please? I assume these are characters that would be back to export to graphite? I thought - was bad
    // too or was it  single qutote https://github.com/dropwizard/metrics/issues/1322
    // There's a GraphiteSanitize class probably should use that too
    private static final Pattern blacklistedChars = Pattern.compile("[{}(),=\\[\\]/]");

    /**
     * I have no idea what this does. I thought the new micrometer stuff went under new. Thhis appears
     * to move the DropWizard stuff there
     */
    private DropwizardConfig dropwizardConfig() {
        return new DropwizardConfig() {
            @Override
            @NonNull
            public String prefix() {
                return "management.metrics.dw-new";
            }

            @Override
            @Nullable
            // Why is this returning null?
            public String get(String s) {
                return null;
            }
        };
    }

    /**
     * Converts from dimensional tags (eg prometheus, micrometer) to graphite (flat)
     * DMITRY: Please comment and explain. Also what's wrong with HierarchicalNameMapper.default
     */
    private HierarchicalNameMapper hierarchicalNameMapper() {
        return (id, convention) -> {
            final StringBuilder tags = new StringBuilder();
            if (!"true".equals(id.getTag("absolute"))) {
                for (Tag tag : id.getTags()) {
                    // Whhy was this commented out, explain what we are trying to achieve
                    tags.append(("." + /*convention.tagKey(tag.getKey()) + "."  + */ convention.tagValue(tag.getValue()))
                        .replace(" ", "_"));
                }
                final String res = "v2." + id.getConventionName(convention) + tags;
                LOG.trace("Hierarchical mapping: {} -> {}", id, res);
                return res;
            }
            return id.getName();
        };
    }

    /**
     * Fixes illegal characters (from graphite's perspective)
     * DMITRY: Why is this named spring2xNamingConvention?
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
                // Normalize Unicode
                String sanitized = Normalizer.normalize(name, Normalizer.Form.NFKD);
                // Then using standard camelcase.
                sanitized = NamingConvention.camelCase.tagKey(sanitized);
                // Then remove some other bad characters
                return blacklistedChars.matcher(sanitized).replaceAll("_");
            }

        };
    }

    @Bean
    public MeterRegistry newDropWizardMeterRegistry(MetricRegistry registry, Clock clock) {
        final DropwizardMeterRegistry res =  new DropwizardMeterRegistry(
                dropwizardConfig(), registry,
                hierarchicalNameMapper(), clock) {
            @Override
            @Nullable
            // DMITRY: is this correct?
            protected Double nullGaugeValue() {
                return null;
            }
        };
        res.config()
            .namingConvention(spring2xNamingConvention())
                // Explain or comment this dmitry.
            .meterFilter(MeterFilter.denyNameStartsWith("jvm"));
        return res;
    }


}
