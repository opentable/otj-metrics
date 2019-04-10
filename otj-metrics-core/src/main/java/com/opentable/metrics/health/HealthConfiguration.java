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
package com.opentable.metrics.health;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.json.HealthCheckModule;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.fasterxml.jackson.databind.Module;

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
        MediocreHealthCheck.class,
        HealthConfiguration.HealthRegistrar.class,
})
public class HealthConfiguration {
    public static final String HEALTH_CHECK_POOL_NAME = "health-check";

    @Bean
    @Named(HEALTH_CHECK_POOL_NAME)
    public ThreadPoolBuilder getHealthCheckPoolBuilder() {
        // Used for running health checks asynchronously. This normally
        // Should be low load anyway, so callerRuns is appropriate backPressure
        // into the servlet pool. An alternative would be an unbounded but fixed
        return ThreadPoolBuilder
                .shortTaskPool(HEALTH_CHECK_POOL_NAME, 8)
                .withDefaultRejectedHandler(ThreadPoolConfig.RejectedHandler.CALLER_RUNS.getHandler());
    }

    @Bean
    @Named("_jackson")
    public Module getHealthCheckModule() {
        return new HealthCheckModule();
    }

    @Bean
    public ServletContextListener getServletContextListener(
            final HealthCheckRegistry registry,
            @Named(HEALTH_CHECK_POOL_NAME) final ExecutorService executor) {
        return new HealthCheckContextListener(registry, executor);
    }

    @Named
    static class HealthRegistrar {
        private final HealthCheckRegistry registry;
        private final Map<String, HealthCheck> checks;

        HealthRegistrar(final HealthCheckRegistry registry, final Map<String, HealthCheck> checks) {
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

    private static class HealthCheckContextListener extends HealthCheckServlet.ContextListener {
        private static final Logger LOG = LoggerFactory.getLogger(HealthCheckContextListener.class);

        private final HealthCheckRegistry registry;
        private final ExecutorService executor;

        HealthCheckContextListener(final HealthCheckRegistry registry,
                                   @Named(HEALTH_CHECK_POOL_NAME) final ExecutorService executor) {
            this.registry = registry;
            this.executor = executor;
        }

        @Override
        protected HealthCheckRegistry getHealthCheckRegistry()
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
