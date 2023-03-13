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

import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Provider;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.metrics.jetty10.InstrumentedQueuedThreadPool;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.opentable.conservedheaders.ConservedHeader;

/**
 * Provides two Beans &mdash; one for a provider of instrumented queued thread pools,
 * and the other for a metrics-instrumented handler customizer.  These are both picked up by {@code EmbeddedJetty}
 * in {@code otj-server}.
 */
@SuppressWarnings("ALL")
@Configuration
public class JettyServerMetricsConfiguration {
    private static final String PREFIX = "http-server";

    /**
     * Create/expose a queued thread pool to use for the Jetty request pool
     * @param metricRegistry the metric registry to use for recording thread pool metrics
     * @param qSize the size of the the thread pool. Configured by "ot.httpserver.queue-size", defaults to 32,768 - THIS IS IGNORED
     * @return the queued thread pool to use for requests
     */
    @Bean
    public Provider<QueuedThreadPool> getIQTPProvider(final MetricRegistry metricRegistry, @Value("${ot.httpserver.queue-size:32768}") int qSize) {
        return () -> {
            final InstrumentedQueuedThreadPool pool = new OTQueuedThreadPool(metricRegistry, qSize);
            pool.setName("default-pool");
            return pool;
        };
    }

    /**
     * Create a consumer that wraps the servers request log with a wrapper that reports metrics for each HTTP status code returned
     * @param metrics registry to register metrics with
     * @return the consumer to add the status code metrics wrapper
     */
    @Bean
    public Consumer<Server> statusReporter(MetricRegistry metrics) {
        return server -> {
            server.setRequestLog(new StatusCodeMetrics(server.getRequestLog(), metrics, PREFIX));
        };
    }

    /**
     * Create a {@link Handler} customizer that wraps the handler in an {@link OTInstrumentedHandler} which report metrics for the handler
     * @param metrics metric registry to register the metrics on
     * @return a Handler customizer to add metrics to the Handler
     */
    @Bean
    public Function<Handler, Handler> getHandlerCustomizer(final MetricRegistry metrics) {
        return handler -> {
            final OTInstrumentedHandler instrumented = new OTInstrumentedHandler(metrics, PREFIX);
            instrumented.setHandler(handler);
            return instrumented;
        };
    }

    /**
     * Instrumented Queued Thread Pool that removes request ID from the MDC after the job is run
     */
    static class OTQueuedThreadPool extends InstrumentedQueuedThreadPool {

        /**
         * Create a queued thread pool. Handles request Id in MDC.
         *
         * @param metricRegistry the metric registry used to record metrics on pool utilization
         * @param qSize the size of the request queue (used if there are no available threads) - THIS IS NOT USED, the queue is unbounded to avoid Jetty memory leaks after a full queue
         */
        OTQueuedThreadPool(MetricRegistry metricRegistry, int qSize) {
            super(metricRegistry,
                32, 32, // Default number of threads, overridden in otj-server EmbeddedJetty
                30000,  // Idle timeout, irrelevant since max == min
                // NB: we originally wished to size this queue, but unfortunately Jetty leaks resources
                // when you throw RejectedExecutionException due to a full work queue.
                // So we leave it unbounded since even an OOME is preferable.
                new BlockingArrayQueue<>());
        }

        @Override
        protected void runJob(Runnable job) {
            try {
                job.run();
            } finally {
                MDC.remove(ConservedHeader.REQUEST_ID.getMDCKey());
            }
        }
    }
}
