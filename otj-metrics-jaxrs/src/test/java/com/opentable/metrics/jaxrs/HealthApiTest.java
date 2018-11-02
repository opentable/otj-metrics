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
package com.opentable.metrics.jaxrs;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import com.opentable.metrics.http.HealthController;
import com.opentable.metrics.jaxrs.HealthResource;
import com.opentable.metrics.jaxrs.HealthResource.SortedEntry;

public class HealthApiTest {
    private final HealthCheckRegistry registry = new HealthCheckRegistry();
    private final HealthController controller = new HealthController(registry, MoreExecutors.newDirectExecutorService(),
            new MockEnvironment().withProperty(
                    "ot.metrics.health.group.mygroup", "a,c"
            ));
    private final HealthResource resource = new HealthResource(controller);

    @Test
    public void testOk() {
        registry.register("a", new Healthy());

        Response r = resource.getHealth(false);
        assertEquals(200, r.getStatus());
        assertEquals(1, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testOneBad() {
        registry.register("a", new Unhealthy());

        Response r = resource.getHealth(false);
        assertEquals(500, r.getStatus());
        assertEquals(1, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testMixed() {
        registry.register("a", new Healthy());
        registry.register("b", new Unhealthy());
        registry.register("c", new Healthy());

        Response r = resource.getHealth(true);
        assertEquals(500, r.getStatus());
        assertEquals(3, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testGroupHappyCase() {
        registry.register("a", new Healthy());
        registry.register("b", new Unhealthy());
        registry.register("c", new Healthy());

        Response r2 = resource.getHealthGroup("mygroup", false);
        assertEquals(200, r2.getStatus());
        assertEquals(2, ((Map<?,?>) r2.getEntity()).size());
        r2.close();
    }

    @Test
    public void testGroupUnknownGroupIs404() {
        registry.register("a", new Healthy());
        registry.register("b", new Unhealthy());
        registry.register("c", new Healthy());

        Response r2 = resource.getHealthGroup("nogroup", true);
        assertEquals(404, r2.getStatus());
        assertEquals(null, r2.getEntity());
        r2.close();
    }

    @Test
    public void testGroupUnhealthy() {
        registry.register("a", new Healthy());
        registry.register("b", new Healthy());
        registry.register("c", new Unhealthy());

        Response r2 = resource.getHealthGroup("mygroup", true);
        assertEquals(500, r2.getStatus());
        assertEquals(2, ((Map<?,?>) r2.getEntity()).size());
        r2.close();
    }

    @Test
    public void testWarning() {
        registry.register("a", new Healthy());
        registry.register("b", new Warning());

        Response r = resource.getHealth(true);
        assertEquals(400, r.getStatus());
        assertEquals(2, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testBadTrumpsWarn() {
        registry.register("a", new Healthy());
        registry.register("b", new Warning());
        registry.register("c", new Unhealthy());

        Response r = resource.getHealth(true);
        assertEquals(500, r.getStatus());
        assertEquals(3, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testHideSuccesses() {
        registry.register("a", new Healthy());
        registry.register("b", new Warning());
        registry.register("c", new Unhealthy());

        Response r = resource.getHealth(false);
        assertEquals(500, r.getStatus());
        Map<?, ?> result = (Map<?,?>) r.getEntity();
        assertEquals(ImmutableList.of("c", "b"), result.keySet().stream()
                .map(e -> SortedEntry.class.cast(e))
                .map(e -> e.getName())
                .collect(Collectors.toList()));
        r.close();
    }

    @Test
    public void testMalformedWarn() {
        registry.register("a", new Healthy());
        registry.register("b", new OopsWarning());

        Response r = resource.getHealth(true);
        assertEquals(500, r.getStatus());
        assertEquals(2, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testTransitionToHealthy() {
        final Iterator<Result> results = ImmutableList.of(
                Result.healthy(),
                Result.unhealthy("failure"),
                Result.unhealthy((String) null),
                Result.healthy()).iterator();

        registry.register("a", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return results.next();
            }
        });

        while (results.hasNext()) {
            resource.getHealth(true);
        }
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

    static class Warning extends HealthCheck {
        @Override
        protected Result check() throws Exception {
            return Result.unhealthy("WARN: blop");
        }
    }

    static class OopsWarning extends HealthCheck {
        @Override
        protected Result check() throws Exception {
            return Result.unhealthy("I meant to put WARN: at the start");
        }
    }
}
