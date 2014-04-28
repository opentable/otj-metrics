package com.opentable.metrics.http;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.Inject;

@Path("/service-status")
public class MetricsHttpResource
{
    private final MetricRegistry metrics;
    private final HealthCheckRegistry health;

    @Inject
    MetricsHttpResource(MetricRegistry metrics, HealthCheckRegistry health)
    {
        this.metrics = metrics;
        this.health = health;
    }

    @GET
    public List<MonitorResponse> get()
    {
        final List<MonitorResponse> responses = new ArrayList<>();
        metrics.getMetrics().forEach((n, m) -> responses.add(toResponse(n, m)));
        health.runHealthChecks().forEach((n, r) -> responses.add(toResponse(n, r)));
        return responses;
    }

    @GET
    @Path("/{metric-name}")
    public MonitorResponse get(@PathParam("metric-name") String metricName)
    {
        final Metric metric = metrics.getMetrics().get(metricName);
        if (metric != null) {
            return toResponse(metricName, metric);
        }
        if (health.getNames().contains(metricName)) {
            return toResponse(metricName, health.runHealthCheck(metricName));
        }
        return null;
    }

    private MonitorResponse toResponse(String name, Metric metric)
    {
        throw new UnsupportedOperationException(); // TODO
    }

    private MonitorResponse toResponse(String name, HealthCheck.Result result)
    {
        throw new UnsupportedOperationException(); // TODO
    }
}
