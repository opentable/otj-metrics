package com.opentable.metrics;

import java.util.Arrays;
import java.util.EventObject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
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
    private String metricPrefix;

    private MetricSet built;

    public MetricSetBuilder() { }

    /**
     * @param registry the registry to register metrics on
     */
    public MetricSetBuilder(MetricRegistry registry) {
        this.registry = registry;
    }

    /**
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
     * @param metricPrefix a prefix to prepend to name for metrics created from this call forward
     * @return this
     */
    public MetricSetBuilder setPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
        return this;
    }

    /**
     * @param name the name of the meter to create
     * @return a new meter
     */
    public Meter meter(String name) {
        return create(name, Meter::new);
    }

    /**
     * @param name the name of the timer to create
     * @return a new timer
     */
    public Timer timer(String name) {
        return create(name, Timer::new);
    }

    /**
     * @param name the name of the gauge to create
     * @return a new {@link AtomicLongGauge}
     */
    public AtomicLong longGauge(String name) {
        return create(name, AtomicLongGauge::new);
    }

    /**
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
     * @param name the name of the metric to create
     * @param factory the factory for instances
     * @return the created metric
     */
    public <T extends Metric> T create(String name, Supplier<T> factory) {
        final T result = factory.get();
        mapBuilder.put(metricPrefix + name, result);
        return result;
    }

    /**
     * Produce the metric set, and register its metrics
     * either immediately or on the configured event trigger.
     * @return the resulting metric set
     */
    public synchronized MetricSet build() {
        if (built != null) {
            return built;
        }
        final BuiltMetricSet result = new BuiltMetricSet(this);
        if (eventClass == null) {
            if (registry == null) {
                throw new IllegalStateException("No metric registry set");
            }
            registry.registerAll(result);
        }
        return built = result;
    }

    public static class BuiltMetricSet implements MetricSet {
        private final ImmutableMap<String, Metric> map;
        private final Class<? extends EventObject> eventClass;

        BuiltMetricSet(MetricSetBuilder builder) {
            map = builder.mapBuilder.build();
            eventClass = builder.eventClass;
        }

        @Override
        public Map<String, Metric> getMetrics() {
            return map;
        }

        public Class<? extends EventObject> getEventClass() {
            return eventClass;
        }

        @Override
        public String toString() {
            return "BuiltMetricSet" + getMetrics().keySet();
        }
    }
}
