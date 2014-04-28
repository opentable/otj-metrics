package com.opentable.metrics.http;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

interface MonitorResponse
{
    String getName();
    default Instant getTime() {
        return Clock.systemUTC().instant();
    }
    Map<String, Object> getMonitors();
}
