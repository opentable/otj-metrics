package com.opentable.metrics.http;

import java.util.Map;

import com.codahale.metrics.health.HealthCheck.Result;
import com.google.common.collect.ImmutableMap;

class HealthCheckResponse implements MonitorResponse
{
    private final String name;
    private final Result result;

    HealthCheckResponse(String name, Result result)
    {
        this.name = name;
        this.result = result;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Map<String, Object> getMonitors()
    {
        return ImmutableMap.of("healthy", result.isHealthy(), "message", result.getMessage());
    }
}
