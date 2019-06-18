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

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.tuple.Pair;

import com.opentable.metrics.http.CheckState;
import com.opentable.metrics.ready.ReadyCheck;
import com.opentable.metrics.ready.ReadyConfiguration;
import com.opentable.metrics.ready.ReadyController;
import com.opentable.metrics.ready.SortedEntry;

@Named
@Produces(MediaType.APPLICATION_JSON)
@Path(ReadyConfiguration.READY_CHECK_PATH)
public class ReadyResource {
    private final ReadyController controller;

    @Inject
    public ReadyResource(ReadyController controller) {
        this.controller = controller;
    }

    @GET
    @Path("/")
    public Response getReady(@QueryParam("all") @DefaultValue("false") boolean all) {
        final Pair<Map<String, ReadyCheck.Result>, CheckState> result = controller.runReadyChecks();
        return Response.status(result.getRight().getHttpStatus()).entity(SortedEntry.ready(all, result.getLeft())).build();
    }

    @GET
    @Path("/group/{group}")
    public Response getReadyGroup(@PathParam("group") String group, @QueryParam("all") @DefaultValue("false") boolean all) {
        final Pair<Map<String, ReadyCheck.Result>, CheckState> result = controller.runReadyChecks(group);
        if (result == null) {
            return Response.status(404).build();
        }
        return Response.status(result.getRight().getHttpStatus()).entity(SortedEntry.ready(all, result.getLeft())).build();
    }

}
