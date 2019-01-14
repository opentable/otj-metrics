package com.opentable.metrics.actuate.micrometer;

import static org.junit.Assert.*;

import com.codahale.metrics.MetricRegistry;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.MeterRegistry;

import com.opentable.metrics.actuate.AbstractTest;

public class OtMicrometerToDropWizardExportConfigurationTest extends AbstractTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MetricRegistry metricRegistry;

    /**
     * Tests metric mapping between {@link MeterRegistry} and {@link MetricRegistry}
     */
    @Test
    public void newDropWizardMeterRegistry() {
        assertNotNull(meterRegistry);
        assertNotNull(metricRegistry);
        meterRegistry.counter("test","tag", "tag").increment();
        assertTrue(metricRegistry.getMetrics()
            .keySet()
            .stream()
            .anyMatch(i -> i.startsWith("v2.test.tag")));
    }
}