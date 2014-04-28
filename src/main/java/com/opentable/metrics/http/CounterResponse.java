package com.opentable.metrics.http;

import java.util.Collections;
import java.util.Map;

import com.codahale.metrics.Counter;

class CounterResponse implements MonitorResponse
{
    private final String name;
    private final Counter counter;

    CounterResponse(String name, Counter counter)
    {
        this.name = name;
        this.counter = counter;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Map<String, Object> getMonitors()
    {
        return Collections.singletonMap("count", counter.getCount());
    }
}
