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
package com.opentable.metrics.ready;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.concurrent.OTExecutors;
import com.opentable.concurrent.ThreadPoolBuilder;
import com.opentable.concurrent.ThreadPoolConfig;

@Configuration
@Import({
        ReadyConfiguration.ReadyRegistrar.class,
})
public class ReadyConfiguration {
    public static final String READY_CHECK_POOL_NAME = "ready-check";

    /**
     * Create a ready check registry to register health checks with
     * @return ready check registry
     */
    @Bean
    public ReadyCheckRegistry getReadyCheckRegistry() {
        return new ReadyCheckRegistry();
    }


    @Bean
    @Named(READY_CHECK_POOL_NAME)
    public ThreadPoolBuilder getReadyCheckPoolBuilder() {
        // Used for running ready checks asynchronously. This normally
        // Should be low load anyway, so callerRuns is appropriate backPressure
        // into the servlet pool. An alternative would be an unbounded but fixed
        return ThreadPoolBuilder
                .shortTaskPool(READY_CHECK_POOL_NAME, 8)
                .withDefaultRejectedHandler(ThreadPoolConfig.RejectedHandler.CALLER_RUNS.getHandler());
    }

    @Bean
    public ServletContextListener getServletContextListener(
            final ReadyCheckRegistry registry,
            @Named(READY_CHECK_POOL_NAME) final ExecutorService executor) {
        return new ReadyCheckContextListener(registry, executor);
    }

    @Named
    static class ReadyRegistrar {
        private final ReadyCheckRegistry registry;
        private final Map<String, ReadyCheck> checks;

        ReadyRegistrar(final ReadyCheckRegistry registry, final Map<String, ReadyCheck> checks) {
            this.registry = registry;
            this.checks = checks;
        }

        @PostConstruct
        private void postConstruct() {
            checks.forEach(registry::register);
        }

        @PreDestroy
        private void preDestroy() {
            checks.keySet().forEach(registry::unregister);
        }
    }

    private static class ReadyCheckContextListener extends ReadyCheckServlet.ContextListener {
        private static final Logger LOG = LoggerFactory.getLogger(ReadyCheckContextListener.class);

        private final ReadyCheckRegistry registry;
        private final ExecutorService executor;

        ReadyCheckContextListener(final ReadyCheckRegistry registry,
                                   @Named(READY_CHECK_POOL_NAME) final ExecutorService executor) {
            this.registry = registry;
            this.executor = executor;
        }

        @Override
        protected ReadyCheckRegistry getReadyCheckRegistry()
        {
            return registry;
        }

        @Override
        protected ExecutorService getExecutorService()
        {
            return executor;
        }

        @Override
        public void contextDestroyed(ServletContextEvent event) {
            super.contextDestroyed(event);
            try {
                if (!OTExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(5))) {
                    LOG.error("executor did not shut down cleanly");
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("interrupted shutting down executor", e);
                // Owning thread is shutting down anyway, no need to re-raise.
            }
        }
    }
}
