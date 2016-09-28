package com.opentable.metrics;

import java.util.Map;
import java.util.TreeMap;

import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;

import org.junit.Assert;

/**
 * Utilities for dealing with {@link Metric}s.
 */
public class MetricUtils {
    private MetricUtils() {}

    /**
     * Attempts to extract a long value from the given metric.
     *
     * @throws IllegalArgumentException if a long value could not be extracted.
     */
    public static long extractLong(final Metric m) {
        if (m instanceof Counting) {
            return ((Counting) m).getCount();
        }
        if (m instanceof Gauge) {
            final Object value = ((Gauge) m).getValue();
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        throw new IllegalArgumentException(String.format("could not extract long value from %s", m.getClass()));
    }

    /**
     * Using {@link #extractLong(Metric)}, extracts long from named metric in metric set.
     *
     * @throws IllegalArgumentException if the named long value could not be extracted.
     */
    public static long extractLong(final MetricSet metricSet, final String metricName) {
        final Metric m = metricSet.getMetrics().get(metricName);
        try {
            return extractLong(m);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(metricName, e);
        }
    }

    /**
     * String formatting of extracted long value of metric.
     *
     * @throws IllegalArgumentException if a long value could not be extracted.
     */
    public static String toString(final Metric m) {
        return Long.toString(extractLong(m));
    }

    /**
     * Like {@link #toString(Metric)}, but defaults to {@link Metric#toString()} if a long can't be extracted.
     */
    public static String toStringSafe(final Metric m) {
        try {
            return toString(m);
        } catch (final IllegalArgumentException e) {
            return m.toString();
        }
    }

    /**
     * Produce sorted map-style {@link #toString()} of metric set, extracting long values from values that support it
     * using {@link #extractLong(Metric)}.  If a long can't be extracted, falls back to using
     * {@link Metric#toString()}.
     */
    public static String toString(final MetricSet metricSet) {
        final Map<String, String> sorted = new TreeMap<>();
        metricSet.getMetrics().forEach((name, metric) -> sorted.put(name, toStringSafe(metric)));
        return sorted.toString();
    }

    /**
     * Testing utility function to assert that a subset of the given {@link MetricSet} metrics have the asserted
     * values.  Values in the {@param metricSet} will be ascertained with {@link #extractLong(MetricSet, String)}.
     * Will extract the {@link Number#longValue()} from the {@param assertions} entries.
     *
     * @param metricSet The metric set against which to assert.
     * @param assertions Mapping of metric name to asserted value.
     */
    public static void assertMetricsEqual(final MetricSet metricSet, final Map<String, Number> assertions) {
        assertions.forEach((name, value) -> {
            final long expected = value.longValue();
            final long actual = extractLong(metricSet, name);
            Assert.assertEquals(String.format("%s assertion failed", name), expected, actual);
        });
    }
}
