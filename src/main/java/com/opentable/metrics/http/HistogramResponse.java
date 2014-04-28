package com.opentable.metrics.http;

import java.util.Map;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Snapshot;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

class HistogramResponse implements MonitorResponse
{
    private final String name;
    private final Histogram metric;

    HistogramResponse(String name, Histogram metric)
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
        final Snapshot snapshot = metric.getSnapshot();
        return fillSnapshotMonitors(ImmutableMap.<String, Object>builder(), snapshot).build();
    }

    static Builder<String, Object> fillSnapshotMonitors(
            ImmutableMap.Builder<String, Object> map, Snapshot snapshot)
    {
        return map.put("min", snapshot.getMin())
                .put("max", snapshot.getMax())
                .put("mean", snapshot.getMean())
                .put("stdDev", snapshot.getStdDev())
                .put("median", snapshot.getMedian())
                .put("tp75", snapshot.get75thPercentile())
                .put("tp95", snapshot.get95thPercentile())
                .put("tp98", snapshot.get98thPercentile())
                .put("tp99", snapshot.get99thPercentile())
                .put("tp999", snapshot.get999thPercentile());
    }
}
