package com.opentable.metrics.graphite;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.google.common.annotations.Beta;

/**
 * Utility class for interacting with {@link MetricSet}s.
 */
@Beta
public final class MetricSets {
    private MetricSets() { }
    /**
     * Create a MetricSet view that returns the given set's metrics, with a name transformer
     * applied to each key.
     */
    public static MetricSet transformNames(MetricSet set, Function<String, String> nameTransformer) {
        return () -> {
            final Map<String, Metric> innerMetrics = set.getMetrics();
            final Map<String, Metric> result = new HashMap<>(innerMetrics.size());
            innerMetrics.forEach((k, v) -> result.put(nameTransformer.apply(k), v));
            return Collections.unmodifiableMap(result);
        };
    }

    /**
     * Create a view that is the summation of multiple {@link MetricSet}s.
     * If more than one metric set has a given key, the value is arbitrary.
     */
    public static MetricSet combine(Iterable<MetricSet> sets) {
        return () -> {
            final Map<String, Metric> result = new HashMap<>();
            sets.forEach(ms -> result.putAll(ms.getMetrics()));
            return Collections.unmodifiableMap(result);
        };
    }

    /**
     * Create a view that is the summation of multiple {@link MetricSet}s.
     * If more than one metric set has a given key, the value is arbitrary.
     */
    public static MetricSet combine(MetricSet... sets) {
        return combine(Arrays.asList(sets));
    }

    /**
     * Return new metric set view that prefixes the names of the entries.
     */
    public static MetricSet prefix(String prefix, MetricSet metricSet) {
        return transformNames(metricSet, k -> prefix + k);
    }

    /**
     * Combine multiple metric sets and prefix their names.
     */
    public static MetricSet combineAndPrefix(String prefix, MetricSet... metricSets) {
        return prefix(prefix, combine(metricSets));
    }

    /**
     * Remove all {@param metrics} from the given {@param metricRegistry}.
     */
    public static void removeAll(MetricRegistry metricRegistry, MetricSet metrics) {
        metrics.getMetrics().keySet().forEach(metricRegistry::remove);
    }
}
