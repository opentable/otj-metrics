package com.opentable.metrics.http;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import javax.ws.rs.core.Response;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Test;

import com.opentable.config.Config;

public class HealthApiTest {
    private final HealthCheckRegistry registry = new HealthCheckRegistry();
    private final HealthController controller = new HealthController(registry, MoreExecutors.newDirectExecutorService(),
            Config.getFixedConfig(
                    "ot.metrics.health.group.mygroup", "a,c"
            ));
    private final HealthResource resource = new HealthResource(controller);

    @Test
    public void testOk() {
        registry.register("a", new Healthy());

        Response r = resource.getHealth();
        assertEquals(200, r.getStatus());
        assertEquals(1, ((Map<?,?>) r.getEntity()).size());
    }

    @Test
    public void testOneBad() {
        registry.register("a", new Unhealthy());

        Response r = resource.getHealth();
        assertEquals(500, r.getStatus());
        assertEquals(1, ((Map<?,?>) r.getEntity()).size());
    }

    @Test
    public void testMixed() {
        registry.register("a", new Healthy());
        registry.register("b", new Unhealthy());
        registry.register("c", new Healthy());

        Response r = resource.getHealth();
        assertEquals(500, r.getStatus());
        assertEquals(3, ((Map<?,?>) r.getEntity()).size());
    }

    @Test
    public void testGroupHappyCase() {
        registry.register("a", new Healthy());
        registry.register("b", new Unhealthy());
        registry.register("c", new Healthy());

        Response r2 = resource.getHealthGroup("mygroup");
        assertEquals(200, r2.getStatus());
        assertEquals(2, ((Map<?,?>) r2.getEntity()).size());
    }

    @Test
    public void testGroupUnknownGroupIs404() {
        registry.register("a", new Healthy());
        registry.register("b", new Unhealthy());
        registry.register("c", new Healthy());

        Response r2 = resource.getHealthGroup("nogroup");
        assertEquals(404, r2.getStatus());
        assertEquals(null, r2.getEntity());
    }

    @Test
    public void testGroupUnhealthy() {
        registry.register("a", new Healthy());
        registry.register("b", new Healthy());
        registry.register("c", new Unhealthy());

        Response r2 = resource.getHealthGroup("mygroup");
        assertEquals(500, r2.getStatus());
        assertEquals(2, ((Map<?,?>) r2.getEntity()).size());
    }

    static class Healthy extends HealthCheck {
        @Override
        protected Result check() throws Exception {
            return Result.healthy();
        }
    }

    static class Unhealthy extends HealthCheck {
        @Override
        protected Result check() throws Exception {
            return Result.unhealthy("wah");
        }
    }
}
