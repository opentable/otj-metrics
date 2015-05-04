package com.opentable.metrics.http;

import java.util.Map;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.health.HealthCheck.Result;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    private final HealthController controller;

    @Inject
    HealthResource(HealthController controller) {
        this.controller = controller;
    }

    @GET
    public Response getHealth() {
        final Map<String, Result> result = controller.runHealthChecks();
        int status = result.values().stream()
                .filter(((Predicate<Result>)Result::isHealthy).negate())
                .findAny()
                .isPresent() ? 500 : 200;
        return Response.status(status).entity(result).build();
    }
}
