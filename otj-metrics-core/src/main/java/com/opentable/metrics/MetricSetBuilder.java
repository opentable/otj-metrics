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

import java.util.Arrays;
import java.util.EventObject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Timer;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * Builder style class for constructing {@link MetricSet}s that integrate
 * with other platform components, such as Spring.
 *
 * Intended to be used standalone or as a prototype scope injected bean.
 * In order to register by event, the built MetricSet must itself be registered
 * as a bean in the Spring context.
 */
public class MetricSetBuilder {
    private final ImmutableMap.Builder<String, Metric> mapBuilder = ImmutableMap.builder();

    private MetricRegistry registry;
    private Class<? extends EventObject> eventClass;
    private String metricPrefix = "";

    private MetricSet built;

    /**
     * Create a Metric Set Builder
     */
    public MetricSetBuilder() { }

    /**
     * Create a Metric Set Builder
     * @param registry the registry to register metrics on
     */
    public MetricSetBuilder(MetricRegistry registry) {
        this.registry = registry;
    }

    /**
     * Set the metric registry. If set, the metric set will be registered on this metric set when built,
     * or if an eventClassSet class is set once the event is fired
     * @param registry the registry to register metrics on
     * @return this
     */
    public synchronized MetricSetBuilder setRegistry(MetricRegistry registry) {
        this.registry = registry;
        return this;
    }

    /**
     * Delays Metric registration until the given event type fires in the Spring
     * application context that created this builder.
     *
     * <p>
     * Automatic registration is implemented in the {@link DefaultMetricsConfiguration}, and thus requires
     * instances of this class be acquired via injection from that configuration class.
     *
     * @param eventClass the event type to await
     * @return this
     */
    public MetricSetBuilder registerOnEvent(Class<? extends EventObject> eventClass) {
        this.eventClass = eventClass;
        return this;
    }

    /**
     * Set a metric prefix to prepend to all metrics created once the prefix is set
     *
     * @param metricPrefix a prefix to prepend to name for metrics created from this call forward;
     *                     defaults to {@code ""} if this isn't called
     * @return this
     */
    public MetricSetBuilder setPrefix(String metricPrefix) {
        Preconditions.checkArgument(!metricPrefix.endsWith("."),
                String.format("prefix '%s' ends with '.'", metricPrefix));
        if (!metricPrefix.isEmpty()) {
            metricPrefix += ".";
        }
        this.metricPrefix = metricPrefix;
        return this;
    }

    /**
     * Create a counter
     * @param name the name of the counter to create
     * @return a new counter
     */
    public Counter counter(String name) {
        return create(name, Counter::new);
    }

    /**
     * Create a meter
     * @param name the name of the meter to create
     * @return a new meter
     */
    public Meter meter(String name) {
        return create(name, Meter::new);
    }

    /**
     * Create a timer
     * @param name the name of the timer to create
     * @return a new timer
     */
    public Timer timer(String name) {
        return create(name, Timer::new);
    }

    /**
     * Create a long gauge
     * @param name the name of the gauge to create
     * @return a new {@link AtomicLongGauge}
     */
    public AtomicLong longGauge(String name) {
        return create(name, AtomicLongGauge::new);
    }

    /**
     * Create a histogram using an {@link ExponentiallyDecayingReservoir}
     * @param name the name of the histogram to create
     * @return a new histogram
     */
    public Histogram histogram(String name) {
        return histogram(name, new ExponentiallyDecayingReservoir());
    }

    /**
     * Create a histogram using with a specific reservoir
     * @param name the name of the histogram to create
     * @param reservoir the reservoir to use for collecting histogram data
     * @return a new histogram
     */
    public Histogram histogram(String name, Reservoir reservoir) {
        return create(name, () -> new Histogram(reservoir));
    }

    /**
     * Create a metric for every value of the gives enum.
     * For each enum value a metric will be created with the name of (given name).(enum name).
     * The metric will be created by the provided factory.
     *
     * @param name the base name for created metrics
     * @param enumClass the enum type to key by
     * @param factory a factory to create metrics
     * @return a map of enum values to metrics
     */
    public <E extends Enum<E>, M extends Metric> Map<E, M> enumMetrics(String name, Class<E> enumClass, Supplier<? extends M> factory) {
        ImmutableMap.Builder<E, M> builder = ImmutableMap.builder();
        Arrays.stream(enumClass.getEnumConstants()).forEach(e ->
                builder.put(e, create(name + "." + e, factory)));
        return builder.build();
    }

    /**
     * Create a metric
     * @param name the name of the metric to create
     * @param factory the factory for instances
     * @return the created metric
     */
    public <T extends Metric> T create(String name, Supplier<T> factory) {
        final T result = factory.get();
        // NB: metricPrefix, if nonempty, will end with '.'.
        mapBuilder.put(metricPrefix + name, result);
        return result;
    }

    /**
     * Produce the metric set; if registry is set, register its metrics
     * either immediately or on the configured event trigger.
     * @return the resulting metric set
     */
    public synchronized MetricSet build() {
        if (built != null) {
            return built;
        }
        final BuiltMetricSet result = new BuiltMetricSet(this);
        if (registry != null && eventClass == null) {
            registry.registerAll(result);
            // Otherwise, if there's still an event class, the machinery in DefaultMetricsConfiguration will
            // govern registration and removal.
        }
        return built = result;
    }

    /**
     * A Metric Set built by the Metric Set Builder
     */
    public static class BuiltMetricSet implements MetricSet {
        private final ImmutableMap<String, Metric> map;
        private final Class<? extends EventObject> eventClass;

        /**
         * Create a built metric set
         * @param builder the metric set builder
         */
        BuiltMetricSet(MetricSetBuilder builder) {
            map = builder.mapBuilder.build();
            eventClass = builder.eventClass;
        }

        @Override
        public Map<String, Metric> getMetrics() {
            return map;
        }

        /**
         * Get the class of event that triggers the registration of this metric
         * @return the event class
         */
        public Class<? extends EventObject> getEventClass() {
            return eventClass;
        }

        @Override
        public String toString() {
            return "BuiltMetricSet" + getMetrics().keySet();
        }
    }
}
