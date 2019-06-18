package com.opentable.metrics.ready;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

import javax.inject.Named;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.concurrent.OTExecutors;

public class ReadyCheckContextListener implements ServletContextListener {
    private static final Logger LOG = LoggerFactory.getLogger(ReadyCheckContextListener.class);
    public static final String READY_CHECK_REGISTRY = ReadyCheckServlet.class.getCanonicalName() + ".registry";
    public static final String READY_CHECK_EXECUTOR = ReadyCheckServlet.class.getCanonicalName() + ".executor";
    public static final String READY_CHECK_FILTER = ReadyCheckServlet.class.getCanonicalName() + ".readyCheckFilter";

    private final ReadyCheckRegistry registry;
    private final ExecutorService executor;

    ReadyCheckContextListener(final ReadyCheckRegistry registry,
                              @Named(ReadyConfiguration.READY_CHECK_POOL_NAME) final ExecutorService executor) {
        this.registry = registry;
        this.executor = executor;
    }

    protected ReadyCheckRegistry getReadyCheckRegistry()
    {
        return registry;
    }
    protected ExecutorService getExecutorService()
    {
        return executor;
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        final ServletContext context = event.getServletContext();
        context.setAttribute(READY_CHECK_REGISTRY, getReadyCheckRegistry());
        context.setAttribute(READY_CHECK_EXECUTOR, getExecutorService());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
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
