package com.opentable.metrics.health;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.json.HealthCheckModule;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

import com.opentable.concurrent.ThreadPoolModule;
import com.opentable.httpserver.HttpServerHandlerBinder;
import com.opentable.jackson.OpenTableObjectMapperBinder;
import com.opentable.metrics.HealthCheckBinder;

public final class HealthModule extends ServletModule
{
    private static final String HEALTH_CHECK_POOL_NAME = "health-check";

    @Override
    protected void configureServlets()
    {
        install (ThreadPoolModule.shortTaskPool(HEALTH_CHECK_POOL_NAME, 8));

        bind (HealthRegistrar.class).asEagerSingleton();

        OpenTableObjectMapperBinder.bindJacksonModule(binder()).to(HealthCheckModule.class);

        bind (HealthCheckContextListener.class).in(Scopes.SINGLETON);
        bind (HealthCheckServlet.class).in(Scopes.SINGLETON);

        HttpServerHandlerBinder.bindServletContextListener(binder()).to(HealthCheckContextListener.class);
        serve("/health").with(HealthCheckServlet.class);

        HealthCheckBinder.bind(binder(), "jvm").to(JvmHealthCheck.class);
    }

    static class HealthRegistrar
    {
        @Inject
        HealthRegistrar(HealthCheckRegistry registry, Map<String, HealthCheck> checks)
        {
            checks.forEach(registry::register);
        }
    }

    static class HealthCheckContextListener extends HealthCheckServlet.ContextListener
    {
        private final HealthCheckRegistry registry;
        private final ExecutorService executor;

        @Inject
        HealthCheckContextListener(HealthCheckRegistry registry, @Named(HEALTH_CHECK_POOL_NAME) ExecutorService executor)
        {
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

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj != null && getClass().equals(obj.getClass());
    }
}
