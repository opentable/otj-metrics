package com.opentable.metrics;

import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Provider;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.codahale.metrics.jetty9.InstrumentedQueuedThreadPool;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides two Beans&mdash;one for a provider of instrumented queued thread pools,
 * and the other for a metrics-instrumented handler customizer.  These are both picked up by {@code EmbeddedJetty}
 * in {@code otj-server}.
 */
@Configuration
public class JettyServerMetricsConfiguration {
    private static final String PREFIX = "http-server";

    @Bean
    public Provider<QueuedThreadPool> getIQTPProvider(final MetricRegistry metricRegistry) {
        return () -> create(metricRegistry);
    }

    private QueuedThreadPool create(final MetricRegistry metricRegistry) {
        final InstrumentedQueuedThreadPool pool = new InstrumentedQueuedThreadPool(metricRegistry,
                32, 32, // Default number of threads, overridden in otj-server EmbeddedJetty
                30000,  // Idle timeout, irrelevant since max == min
                new BlockingArrayQueue<>(128, // Initial queue size
                    8, // Expand increment (irrelevant; initial == max)
                    51200)); // Upper bound on work queue
        pool.setName("default-pool");
        return pool;
    }

    @Bean
    public Consumer<Server> statusReporter(MetricRegistry metrics) {
        return server -> {
            server.setRequestLog(new StatusCodeMetrics(server.getRequestLog(), metrics, PREFIX));
        };
    }

    @Bean
    public Function<Handler, Handler> getHandlerCustomizer(final MetricRegistry metrics) {
        return handler -> {
            final InstrumentedHandler instrumented = new InstrumentedHandler(metrics, PREFIX);
            instrumented.setHandler(handler);
            return instrumented;
        };
    }
}
