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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import com.opentable.metrics.common.SortedEntry;
import com.opentable.metrics.ready.ReadyCheck;
import com.opentable.metrics.ready.ReadyCheckRegistry;
import com.opentable.metrics.ready.ReadyController;
import com.opentable.metrics.ready.Result;

public class ReadyApiTest {
    private final ReadyCheckRegistry registry = new ReadyCheckRegistry();
    private final ReadyController controller = new ReadyController(registry, MoreExecutors.newDirectExecutorService(),
            new MockEnvironment().withProperty(
                    "ot.metrics.ready.group.mygroup", "a,c"
            ));
    private final ReadyResource resource = new ReadyResource(controller);

    @Test
    public void testOk() {
        registry.register("a", new Ready());

        Response r = resource.getReady(false);
        assertEquals(200, r.getStatus());
        assertEquals(1, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testOneBad() {
        registry.register("a", new UnReady());

        Response r = resource.getReady(false);
        assertEquals(500, r.getStatus());
        assertEquals(1, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testMixed() {
        registry.register("a", new Ready());
        registry.register("b", new UnReady());
        registry.register("c", new Ready());

        Response r = resource.getReady(true);
        assertEquals(500, r.getStatus());
        assertEquals(3, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testGroupHappyCase() {
        registry.register("a", new Ready());
        registry.register("b", new UnReady());
        registry.register("c", new Ready());

        Response r2 = resource.getReadyGroup("mygroup", false);
        assertEquals(200, r2.getStatus());
        assertEquals(2, ((Map<?,?>) r2.getEntity()).size());
        r2.close();
    }

    @Test
    public void testGroupUnknownGroupIs404() {
        registry.register("a", new Ready());
        registry.register("b", new UnReady());
        registry.register("c", new Ready());

        Response r2 = resource.getReadyGroup("nogroup", true);
        assertEquals(404, r2.getStatus());
        assertEquals(null, r2.getEntity());
        r2.close();
    }

    @Test
    public void testGroupUnhealthy() {
        registry.register("a", new Ready());
        registry.register("b", new Ready());
        registry.register("c", new UnReady());

        Response r2 = resource.getReadyGroup("mygroup", true);
        assertEquals(500, r2.getStatus());
        assertEquals(2, ((Map<?,?>) r2.getEntity()).size());
        r2.close();
    }

    @Test
    public void testWarning() {
        registry.register("a", new Ready());
        registry.register("b", new Warning());

        Response r = resource.getReady(true);
        assertEquals(400, r.getStatus());
        assertEquals(2, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testBadTrumpsWarn() {
        registry.register("a", new Ready());
        registry.register("b", new Warning());
        registry.register("c", new UnReady());

        Response r = resource.getReady(true);
        assertEquals(500, r.getStatus());
        assertEquals(3, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testHideSuccesses() {
        registry.register("a", new Ready());
        registry.register("b", new Warning());
        registry.register("c", new UnReady());

        Response r = resource.getReady(false);
        assertEquals(500, r.getStatus());
        Map<?, ?> result = (Map<?,?>) r.getEntity();
        assertEquals(ImmutableList.of("c", "b"), result.keySet().stream()
                .map(SortedEntry.class::cast)
                .map(SortedEntry::getName)
                .collect(Collectors.toList()));
        r.close();
    }

    @Test
    public void testMalformedWarn() {
        registry.register("a", new Ready());
        registry.register("b", new OopsWarning());

        Response r = resource.getReady(true);
        assertEquals(500, r.getStatus());
        assertEquals(2, ((Map<?,?>) r.getEntity()).size());
        r.close();
    }

    @Test
    public void testTransitionToHealthy() {
        final Iterator<Result> results = ImmutableList.of(
                Result.ready(),
                Result.unready("failure"),
                Result.unready((String) null),
                Result.ready()).iterator();

        registry.register("a", new ReadyCheck() {
            @Override
            protected Result check() throws Exception {
                return results.next();
            }
        });

        while (results.hasNext()) {
            resource.getReady(true);
        }
    }

    static class Ready extends ReadyCheck {
        @Override
        protected Result check() throws Exception {
            return Result.ready();
        }
    }

    static class UnReady extends ReadyCheck {
        @Override
        protected Result check() throws Exception {
            return Result.unready("wah");
        }
    }

    static class Warning extends ReadyCheck {
        @Override
        protected Result check() throws Exception {
            return Result.unready("WARN: blop");
        }
    }

    static class OopsWarning extends ReadyCheck {
        @Override
        protected Result check() throws Exception {
            return Result.unready("I meant to put WARN: at the start");
        }
    }
}
