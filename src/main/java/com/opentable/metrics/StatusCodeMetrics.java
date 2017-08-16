package com.opentable.metrics;

import com.codahale.metrics.MetricRegistry;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;

/**
 * Jetty {@link RequestLog} wrapper that determines the HTTP status code
 * and reports it as a metric.  The built-in stuff gives you e.g. {@code 2xx-responses}
 * but we want to have it for each individual status to improve monitoring.
 */
class StatusCodeMetrics implements RequestLog {

    private final RequestLog wrapped;
    private final MetricRegistry registry;
    private final String prefix;

    StatusCodeMetrics(RequestLog wrapped, MetricRegistry registry, String prefix) {
        this.wrapped = wrapped;
        this.registry = registry;
        this.prefix = prefix;
    }

    @Override
    public void log(Request request, Response response) {
        final int status = response.getCommittedMetaData().getStatus();
        registry.meter(prefix + '.' + status + "-responses").mark();
        if (wrapped != null) {
            wrapped.log(request, response);
        }
    }
}
