package com.opentable.metrics;

import java.util.function.Function;

import javax.inject.Provider;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.codahale.metrics.jetty9.InstrumentedQueuedThreadPool;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides two Beans&mdash;one for a provider of instrumented queued thread pools,
 * and the other for a metrics-instrumented handler customizer.  These are both picked up by {@code EmbeddedJetty}
 * in {@code otj-server}.
 */
@Configuration
public final class JettyServerMetricsConfiguration {
    @Bean
    public Provider<QueuedThreadPool> getIQTPProvider(final MetricRegistry metrics) {
        return () -> new InstrumentedQueuedThreadPool(metrics);
    }

    @Bean
    public Function<Handler, Handler> getHandlerCustomizer(final MetricRegistry metrics) {
        return handler -> {
            final InstrumentedHandler instrumented = new InstrumentedHandler(metrics);
            instrumented.setHandler(handler);
            return instrumented;
        };
    }
}
