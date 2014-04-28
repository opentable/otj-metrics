package com.opentable.metrics.http;

import java.util.Map;

import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableMap;

class TimerResponse extends MeterResponse
{
    private final Timer metric;

    TimerResponse(String name, Timer metric)
    {
        super(name, metric);
        this.metric = metric;
    }

    @Override
    public Map<String, Object> getMonitors()
    {
        return HistogramResponse.fillSnapshotMonitors(
                fillMeterMonitors(ImmutableMap.<String, Object>builder()),
                metric.getSnapshot()).build();
    }
}
