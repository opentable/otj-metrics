package com.opentable.metrics.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


/**
 * A set of gauges for the count of open and maximum file descriptors.
 * Also includes a set of counters for exceptions that occur when trying to retrieve
 * file descriptors from the OS.
 */
public class FileDescriptorMetricSet implements MetricSet {
    private final List<String> FDTYPE = ImmutableList.of("open", "max");
    private final List<String> EXCEPTION = ImmutableList.of("no-such-method", "illegal-access", "invocation-target");
    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    private final Map<String, Map<String, Counter>> calls;

    public FileDescriptorMetricSet() {
        final Supplier<Map<String, Counter>> makeCounters = () ->
                ImmutableMap.of(
                        "no-such-method", new Counter(),
                        "illegal-access", new Counter(),
                        "invocation-target", new Counter()
                );

        calls = ImmutableMap.of(
                "open", makeCounters.get(),
                "max", makeCounters.get()
        );
    }

    @Override
    public Map<String, Metric> getMetrics() {
        final Map<String, Metric> gauges = new HashMap<>();

        gauges.put(FDTYPE.get(0), (Gauge<Long>)() -> invoke("getOpenFileDescriptorCount"));
        gauges.put(FDTYPE.get(1), (Gauge<Long>)() -> invoke("getMaxFileDescriptorCount"));

        for (String type: FDTYPE) {
            for(String ex: EXCEPTION) {
                gauges.put(String.format("%s.%s.%s", "exception", type, ex), calls.get(type).get(ex));
            }
        }
        return Collections.unmodifiableMap(gauges);
    }

    private Long invoke(String name) {
        String fdType =  (name.contains("open")) ? FDTYPE.get(0) : FDTYPE.get(1);
        try {
            final Method method = os.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return (Long) method.invoke(os);
        } catch (NoSuchMethodException e) {
            calls.get(fdType).get(EXCEPTION.get(0)).inc();
        } catch (IllegalAccessException e) {
            calls.get(fdType).get(EXCEPTION.get(1)).inc();
        } catch (InvocationTargetException e) {
            calls.get(fdType).get(EXCEPTION.get(2)).inc();
        }
        return null;
    }
}
