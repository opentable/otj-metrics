package com.opentable.metrics.actuate.health;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.opentable.metrics.actuate.AbstractTest;

public class CodahaleHealthIndicatorConfigurationTest extends AbstractTest {

    @Test
    public void actuatorHealthEndpoint() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.details.dropWizard.status", is(equalTo("UP"))));
    }
}