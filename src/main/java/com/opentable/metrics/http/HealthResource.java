package com.opentable.metrics.http;

import java.util.Map;
import java.util.TreeMap;

import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.health.HealthCheck.Result;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ComparisonChain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.tuple.Pair;

import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;

@Named
@Produces(MediaType.APPLICATION_JSON)
@Path("/health")
public class HealthResource {
    private final HealthController controller;
    private final TargetLengthBasedClassNameAbbreviator abbreviator = new TargetLengthBasedClassNameAbbreviator(20);

    HealthResource(HealthController controller) {
        this.controller = controller;
    }

    @GET
    @Path("/")
    public Response getHealth() {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks();
        return Response.status(result.getRight().getHttpStatus()).entity(render(result.getLeft())).build();
    }

    @GET
    @Path("/group/{group}")
    public Response getHealthGroup(@PathParam("group") String group) {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks(group);
        if (result == null) {
            return Response.status(404).build();
        }
        return Response.status(result.getRight().getHttpStatus()).entity(render(result.getLeft())).build();
    }

    private Map<SortedEntry, Result> render(Map<String, Result> raw) {
        Map<SortedEntry, Result> rendered = new TreeMap<>();
        raw.forEach((name, result) -> {
            rendered.put(new SortedEntry(abbreviator.abbreviate(name), result), result);
        });
        return rendered;
    }

    static class SortedEntry implements Comparable<SortedEntry> {
        final String name;
        final Result result;

        SortedEntry(String name, Result result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public int compareTo(SortedEntry o) {
            return ComparisonChain.start()
                    // severity descending
                    .compare(o.result, result, HealthController::compare)
                    // name ascending
                    .compare(name, o.name)
                    .result();
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return EqualsBuilder.reflectionEquals(this, obj, false);
        }

        @Override
        @JsonValue
        public String toString() {
            return name;
        }
    }
}
