package com.opentable.metrics.http;

import java.util.Map;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlets.AdminServlet;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

import com.opentable.httpserver.HttpServerHandlerBinder;

public class MetricsHttpModule extends ServletModule
{
    @Override
    protected void configureServlets()
    {
        bind (MetricsHttpResource.class);

        final Map<String, String> initParams = ImmutableMap.<String, String>builder()
                .put("metrics-uri", "/metrics/metrics")
                .put("ping-uri", "/metrics/ping")
                .put("threads-uri", "/metrics/threads")
                .put("healthcheck-uri", "/metrics/health")
            .build();

        serve ("/metrics").with(AdminServlet.class, initParams);

        HttpServerHandlerBinder.bindServletContextListener(binder())
            .to(MetricsContextListener.class).in(Scopes.SINGLETON);
        HttpServerHandlerBinder.bindServletContextListener(binder())
            .to(HealthContextListener.class).in(Scopes.SINGLETON);
    }

    private static class MetricsContextListener extends MetricsServlet.ContextListener
    {
        private final MetricRegistry metrics;

        @Inject
        MetricsContextListener(MetricRegistry metrics)
        {
            this.metrics = metrics;
        }

        @Override
        protected MetricRegistry getMetricRegistry()
        {
            return metrics;
        }
    }

    private static class HealthContextListener extends HealthCheckServlet.ContextListener
    {
        private final HealthCheckRegistry health;

        @Inject
        HealthContextListener(HealthCheckRegistry health)
        {
            this.health = health;
        }

        @Override
        public HealthCheckRegistry getHealthCheckRegistry()
        {
            return health;
        }
    }
}
