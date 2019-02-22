package com.opentable.metrics.jaxrs;
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


import java.util.Map;
import java.util.TreeMap;

import javax.inject.Named;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.health.HealthCheck.Result;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.opentable.metrics.SortedEntry;
import com.opentable.metrics.http.CheckState;
import com.opentable.metrics.http.HealthController;

@Named
@Produces(MediaType.APPLICATION_JSON)
@Path("/health")
public class HealthResource {
    private final HealthController controller;

    public HealthResource(HealthController controller) {
        this.controller = controller;
    }

    @GET
    @Path("/")
    public Response getHealth(@QueryParam("all") @DefaultValue("false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks();
        return Response.status(result.getRight().getHttpStatus()).entity(render(all, result.getLeft())).build();
    }

    @GET
    @Path("/group/{group}")
    public Response getHealthGroup(@PathParam("group") String group, @QueryParam("all") @DefaultValue("false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks(group);
        if (result == null) {
            return Response.status(404).build();
        }
        return Response.status(result.getRight().getHttpStatus()).entity(render(all, result.getLeft())).build();
    }

    private Map<SortedEntry, Result> render(boolean all, Map<String, Result> raw) {
        Map<SortedEntry, Result> rendered = new TreeMap<>();
        raw.forEach((name, result) -> {
            rendered.put(new SortedEntry(ClassUtils.getAbbreviatedName(name, 20), result), result);
        });

        if (!all && !rendered.isEmpty()) {
            Result worstResult = rendered.keySet().iterator().next().getResult();
            if (!worstResult.isHealthy()) {
                rendered.keySet().removeIf(e -> e.getResult().isHealthy());
            }
        }

        return rendered;
    }

}
