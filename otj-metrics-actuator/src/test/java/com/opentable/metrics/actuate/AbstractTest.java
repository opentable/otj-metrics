package com.opentable.metrics.actuate;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestMvcServer2Configuration.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles(profiles = "test")
@TestPropertySource(properties = {
    "spring.jmx.enabled=true",
    "management.endpoints.web.exposure.include=*",
    "management.endpoint.health.show-details=always",
    "management.metrics.export.dw-new.enabled=true"
})
public abstract class AbstractTest {

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mvc;

    @Before
    public void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .build();
    }

}