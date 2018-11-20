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
package com.opentable.metrics.mvc;

import java.util.ArrayList;
import java.util.List;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opentable.metrics.http.CounterResponse;
import com.opentable.metrics.http.GaugeResponse;
import com.opentable.metrics.http.HealthCheckResponse;
import com.opentable.metrics.http.HistogramResponse;
import com.opentable.metrics.http.MeterResponse;
import com.opentable.metrics.http.MonitorResponse;
import com.opentable.metrics.http.TimerResponse;

@RestController
@RequestMapping("/service-status")
public class MetricsHttpEndpoint
{
    private final MetricRegistry metrics;
    private final HealthCheckRegistry health;

    @Autowired
    MetricsHttpEndpoint(MetricRegistry metrics, HealthCheckRegistry health)
    {
        this.metrics = metrics;
        this.health = health;
    }

    @GetMapping
    public List<MonitorResponse> get()
    {
        final List<MonitorResponse> responses = new ArrayList<>();
        metrics.getMetrics().forEach((n, m) -> responses.add(toResponse(n, m)));
        health.runHealthChecks().forEach((n, r) -> responses.add(toResponse(n, r)));
        return responses;
    }

    @GetMapping("/{metric-name}")
    public MonitorResponse get(@PathVariable("metric-name") String metricName)
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
