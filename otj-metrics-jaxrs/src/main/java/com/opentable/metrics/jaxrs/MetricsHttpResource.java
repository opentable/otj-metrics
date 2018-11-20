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

import com.opentable.metrics.http.CounterResponse;
import com.opentable.metrics.http.GaugeResponse;
import com.opentable.metrics.http.HealthCheckResponse;
import com.opentable.metrics.http.HistogramResponse;
import com.opentable.metrics.http.MeterResponse;
import com.opentable.metrics.http.MonitorResponse;
import com.opentable.metrics.http.TimerResponse;

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
