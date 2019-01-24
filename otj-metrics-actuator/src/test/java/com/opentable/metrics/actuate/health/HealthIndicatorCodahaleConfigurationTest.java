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
package com.opentable.metrics.actuate.health;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.opentable.metrics.actuate.AbstractTest;

public class HealthIndicatorCodahaleConfigurationTest extends AbstractTest {

    /**
     * Tests presence of the diskSpaceHealthIndicator in the standard health endpoint
     */
    @Test
    public void codahaleHealthEndpoint() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/health"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.diskSpaceHealthIndicator.healthy", is(equalTo(true))));
    }

}