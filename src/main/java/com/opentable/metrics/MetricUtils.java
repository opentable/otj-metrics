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
package com.opentable.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.Timer;
import com.google.common.collect.Multiset;

/**
 * Utilities for dealing with {@link Metric}s.
 */
public final class MetricUtils {
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
            final Object value = ((Gauge<?>) m).getValue();
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
     * Update a map of metrics with counts from an accumulated Multiset.
     * @param metricMap the registered metrics to update with new values
     * @param counts the new values to emit
     */
    public static <E extends Enum<E>> void updateEnumMetrics(Map<E, ? extends AtomicLong> metricMap, Multiset<E> counts) {
        metricMap.entrySet().stream().forEach(e -> e.getValue().set(counts.count(e.getKey())));
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
     * @throws AssertionError On the first assertion failure.
     */
    public static void assertMetricsEqual(final MetricSet metricSet, final Map<String, Number> assertions)
            throws AssertionError {
        assertions.forEach((name, value) -> {
            final long expected = value.longValue();
            final long actual = extractLong(metricSet, name);
            if (expected != actual) {
                throw new AssertionError(
                        String.format("%s assertion failed; expected %d != actual %d", name, expected, actual));
            }
        });
    }

    /**
     * Convenience function to return a {@link Timer} using a {@link SlidingTimeWindowReservoir} with a window
     * size specified by the {@link Duration} passed in.
     * @param window the size of the {@link SlidingTimeWindowReservoir} window
     * @return timer using a {@link SlidingTimeWindowReservoir} with window size as specified
     */
    public static Timer slidingTimer(final Duration window) {
        return new Timer(new SlidingTimeWindowReservoir(window.toNanos(), TimeUnit.NANOSECONDS));
    }
}
