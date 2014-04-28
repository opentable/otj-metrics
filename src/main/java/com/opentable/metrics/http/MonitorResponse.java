package com.opentable.metrics.http;

import java.time.Instant;
import java.util.Map;

interface MonitorResponse
{
    String getName();
    Instant getTime();
    Map<String, Object> getMonitors();
}
