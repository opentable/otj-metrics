package com.opentable.metrics.actuate.legacy;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class WebMvcLegacyMetricsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebMvcLegacyMetricsFilter.class);

    private static final String ATTRIBUTE_STOP_WATCH = WebMvcLegacyMetricsFilter.class.getName() + ".StopWatch";

    private static final int UNDEFINED_HTTP_STATUS = 999;

    private static final String UNKNOWN_PATH_SUFFIX = "/unmapped";

    private final MetricRegistry metricRegistry;

    private static final Set<PatternReplacer> STATUS_REPLACES;

    static {
        Set<PatternReplacer> replacements = new LinkedHashSet<PatternReplacer>();
        replacements.add(new PatternReplacer("[{}]", 0, "-"));
        replacements.add(new PatternReplacer("**", Pattern.LITERAL, "-star-star-"));
        replacements.add(new PatternReplacer("*", Pattern.LITERAL, "-star-"));
        replacements.add(new PatternReplacer("/-", Pattern.LITERAL, "/"));
        replacements.add(new PatternReplacer("-/", Pattern.LITERAL, "/"));
        STATUS_REPLACES = Collections.unmodifiableSet(replacements);
    }

    private static final Set<PatternReplacer> KEY_REPLACES;

    static {
        Set<PatternReplacer> replacements = new LinkedHashSet<PatternReplacer>();
        replacements.add(new PatternReplacer("/", Pattern.LITERAL, "."));
        replacements.add(new PatternReplacer("..", Pattern.LITERAL, "."));
        KEY_REPLACES = Collections.unmodifiableSet(replacements);
    }

    @Inject
    public WebMvcLegacyMetricsFilter(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        StopWatch stopWatch = createStopWatchIfNecessary(request);
        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        try {
            chain.doFilter(request, response);
            status = getStatus(response);
        } finally {
            if (!request.isAsyncStarted()) {
                stopWatch.stop();
                request.removeAttribute(ATTRIBUTE_STOP_WATCH);
                if (!"OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    recordMetrics(request, status, stopWatch.getTotalTimeMillis());
                }
            }
        }
    }

    private StopWatch createStopWatchIfNecessary(HttpServletRequest request) {
        StopWatch stopWatch = (StopWatch) request.getAttribute(ATTRIBUTE_STOP_WATCH);
        if (stopWatch == null) {
            stopWatch = new StopWatch();
            stopWatch.start();
            request.setAttribute(ATTRIBUTE_STOP_WATCH, stopWatch);

        }
        return stopWatch;
    }

    private int getStatus(HttpServletResponse response) {
        try {
            return response.getStatus();
        } catch (Exception ex) {
            return UNDEFINED_HTTP_STATUS;
        }
    }

    private void recordMetrics(HttpServletRequest request, int status, long time) {
        String suffix = determineMetricNameSuffix(request);
        submitToGauge(getKey("histogram.response" + suffix), time);
        incrementCounter(getKey("counter.status." + status + suffix));
    }

    private String determineMetricNameSuffix(HttpServletRequest request) {
        Object bestMatchingPattern = request
            .getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestMatchingPattern != null) {
            return fixSpecialCharacters(bestMatchingPattern.toString());
        }
        return UNKNOWN_PATH_SUFFIX;
    }

    private String fixSpecialCharacters(String value) {
        String result = value;
        for (PatternReplacer replacer : STATUS_REPLACES) {
            result = replacer.apply(result);
        }
        if (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.startsWith("-")) {
            result = result.substring(1);
        }
        return result;
    }


    private String getKey(String string) {
        // graphite compatible metric names
        String key = string;
        for (PatternReplacer replacer : KEY_REPLACES) {
            key = replacer.apply(key);
        }
        if (key.endsWith(".")) {
            key = key + "root";
        }
        if (key.startsWith("_")) {
            key = key.substring(1);
        }
        return key;

    }

    private void submitToGauge(String metricName, long value) {
        try {
            log.trace("histogram: histogram('{}').update({})", metricName, value);
            this.metricRegistry.histogram(metricName).update(value);
        } catch (Exception ex) {
            log.warn("Unable to submit histogram metric '" + metricName + "'", ex);
        }
    }


    private void incrementCounter(String metricName) {
        try {
            log.trace("counter: counter('{}').inc()", metricName);
            this.metricRegistry.counter(metricName).inc();
        } catch (Exception ex) {
            log.warn("Unable to submit counter metric '" + metricName + "'", ex);
        }
    }

    private static class PatternReplacer {

        private final Pattern pattern;

        private final String replacement;

        PatternReplacer(String regex, int flags, String replacement) {
            this.pattern = Pattern.compile(regex, flags);
            this.replacement = replacement;
        }

        public String apply(String input) {
            return this.pattern.matcher(input)
                .replaceAll(Matcher.quoteReplacement(this.replacement));
        }
    }
}
