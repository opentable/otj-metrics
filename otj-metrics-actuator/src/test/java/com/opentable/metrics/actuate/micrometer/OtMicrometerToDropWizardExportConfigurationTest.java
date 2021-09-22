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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.codahale.metrics.MetricRegistry;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import io.micrometer.core.instrument.MeterRegistry;

import com.opentable.httpheaders.OTHeaders;
import com.opentable.metrics.actuate.AbstractTest;

@TestPropertySource(properties = {
        "management.metrics.export.dw-new.referringServiceTracking[0]=blah"
}
)
public class OtMicrometerToDropWizardExportConfigurationTest extends AbstractTest {

    private static final Logger log = LoggerFactory.getLogger(OtMicrometerToDropWizardExportConfigurationTest.class);

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MetricRegistry metricRegistry;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Tests metric mapping between {@link MeterRegistry} and {@link MetricRegistry}
     */
    @Test
    public void newDropWizardMeterRegistry() {
        assertNotNull(meterRegistry);
        assertNotNull(metricRegistry);
        meterRegistry.counter("test", "tag", "tag").increment();
        assertTrue(metricRegistry.getMetrics()
                .keySet()
                .stream()
                .anyMatch(i -> i.startsWith("v2.test.tag")));
    }

    @Test
    public void referringServiceMetricTest() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(OTHeaders.REFERRING_SERVICE, "blah");
        final HttpEntity<String> entity = new HttpEntity<>(headers);
        restTemplate.exchange("/actuator/health", HttpMethod.GET, entity, String.class);
        assertTrue(metricRegistry.getMetrics()
                .keySet()
                .stream()
                .anyMatch(i -> i.startsWith("v2.httpServerRequests.None.GET.SUCCESS.blah.")));

    }
}