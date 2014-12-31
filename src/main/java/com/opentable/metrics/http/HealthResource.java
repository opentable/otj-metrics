package com.opentable.metrics.http;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.health.HealthCheck;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    private final HealthController controller;

    @Inject
    HealthResource(HealthController controller) {
        this.controller = controller;
    }

    @GET
    public Map<String, HealthCheck.Result> getHealth() {
        return controller.runHealthChecks();
    }
}
