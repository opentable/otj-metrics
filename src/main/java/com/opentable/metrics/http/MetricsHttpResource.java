package com.opentable.metrics.http;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;

@Named
@Path("/service-status")
@Produces(MediaType.APPLICATION_JSON)
public class MetricsHttpResource
{
    private final MetricRegistry metrics;
    private final HealthCheckRegistry health;

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
        if (metric instanceof Counter) {
            return new CounterResponse(name, (Counter) metric);
        }
        if (metric instanceof Gauge) {
            return new GaugeResponse(name, (Gauge<?>) metric);
        }
        if (metric instanceof Histogram) {
            return new HistogramResponse(name, (Histogram) metric);
        }
        if (metric instanceof Timer) {
            return new TimerResponse(name, (Timer) metric);
        }
        if (metric instanceof Meter) {
            return new MeterResponse(name, (Meter) metric);
        }
        throw new IllegalStateException("don't know how to handle '" + name + "': " + metric.getClass());
    }

    private MonitorResponse toResponse(String name, HealthCheck.Result result)
    {
        return new HealthCheckResponse(name, result);
    }
}
