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