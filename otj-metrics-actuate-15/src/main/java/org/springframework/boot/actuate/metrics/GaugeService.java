package org.springframework.boot.actuate.metrics;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;

import com.google.common.util.concurrent.AtomicDouble;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;


/**
 * 1.5.x actuator compatibility
 *
 * @deprecated use {@link MeterRegistry} instead.
 */
@Component
@Deprecated
public class GaugeService {

    private static final List<Tag> TAGS = Collections.singletonList(Tag.of("absolute", "true"));
    private final ConcurrentMap<String, AtomicDouble> gauges = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    @Inject
    public GaugeService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private void setGaugeValue(String metricName, double value) {
        AtomicDouble gauge = this.gauges.get(metricName);
        if (gauge == null) {
            AtomicDouble newGauge = new AtomicDouble(value);
            gauge = this.gauges.putIfAbsent(metricName, newGauge);
            if (gauge == null) {
                meterRegistry.gauge(metricName, TAGS, newGauge);
                return;
            }
        }
        gauge.set(value);
    }

    /**
     * 1.5.x actuator compatibility
     *
     * @deprecated use {@link MeterRegistry#gauge(String, Number)} instead.
     */
    @Deprecated
    public void submit(String metricName, double value) {
        this.setGaugeValue(metricName, value);
    }

}
