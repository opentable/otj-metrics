package com.opentable.metrics.http;

import java.util.Collections;
import java.util.Map;

import com.codahale.metrics.Gauge;

public class GaugeResponse implements MonitorResponse
{
    private final String name;
    private final Gauge<?> metric;

    GaugeResponse(String name, Gauge<?> metric)
    {
        this.name = name;
        this.metric = metric;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Map<String, Object> getMonitors()
    {
        return Collections.singletonMap("value", metric.getValue());
    }
}
