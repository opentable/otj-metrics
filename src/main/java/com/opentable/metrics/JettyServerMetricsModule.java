package com.opentable.metrics;

import java.util.function.Function;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.codahale.metrics.jetty9.InstrumentedQueuedThreadPool;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.opentable.httpserver.HttpServerHandlerBinder;
import com.opentable.httpserver.HttpServerModule;

public class JettyServerMetricsModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        HttpServerModule.bindThreadPool(binder()).toProvider(IQTPProvider.class);
        HttpServerHandlerBinder.bindHandlerCustomizer(binder()).to(HandlerCustomizer.class);
    }

    static class IQTPProvider implements Provider<QueuedThreadPool>
    {
        private final MetricRegistry metrics;

        @Inject
        IQTPProvider(MetricRegistry metrics)
        {
            this.metrics = metrics;
        }

        @Override
        public QueuedThreadPool get()
        {
            return new InstrumentedQueuedThreadPool(metrics);
        }
    }

    static class HandlerCustomizer implements Function<Handler, Handler>
    {
        private final MetricRegistry metrics;

        @Inject
        HandlerCustomizer(MetricRegistry metrics)
        {
            this.metrics = metrics;
        }

        @Override
        public Handler apply(Handler handler)
        {
            final InstrumentedHandler instrumented = new InstrumentedHandler(metrics);
            instrumented.setHandler(handler);
            return instrumented;
        }
    }
}
