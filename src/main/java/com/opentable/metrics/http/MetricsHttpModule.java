package com.opentable.metrics.http;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.AdminServlet;
import com.codahale.metrics.servlets.MetricsServlet;
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

        serve ("/metrics*").with(AdminServlet.class);
        bind (AdminServlet.class).in(Scopes.SINGLETON);

        HttpServerHandlerBinder.bindServletContextListener(binder())
            .to(MetricsContextListener.class).in(Scopes.SINGLETON);
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
}
