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
