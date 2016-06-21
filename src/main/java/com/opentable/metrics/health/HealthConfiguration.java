package com.opentable.metrics.health;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.servlet.ServletContextListener;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.json.HealthCheckModule;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.fasterxml.jackson.databind.Module;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.concurrent.ThreadPoolBuilder;

@Configuration
@Import(HealthConfiguration.HealthRegistrar.class)
public final class HealthConfiguration {
    public static final String HEALTH_CHECK_POOL_NAME = "health-check";

    @Bean
    @Named(HEALTH_CHECK_POOL_NAME)
    public ExecutorService getHealthCheckPool() {
        return ThreadPoolBuilder.shortTaskPool(HEALTH_CHECK_POOL_NAME, 8).build();
    }

    @Bean
    @Named("jvm")
    public JvmHealthCheck getJvmHealthCheck() {
        return new JvmHealthCheck();
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
    }

    private static class HealthCheckContextListener extends HealthCheckServlet.ContextListener {
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
    }
}
