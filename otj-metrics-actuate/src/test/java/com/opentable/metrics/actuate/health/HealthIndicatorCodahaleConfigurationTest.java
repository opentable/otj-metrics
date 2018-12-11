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

    @Test
    public void codahaleHealthEndpoint() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/health"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.diskSpaceHealthIndicator.healthy", is(equalTo(true))));
    }

}