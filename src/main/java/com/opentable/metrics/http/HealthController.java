package com.opentable.metrics.http;

import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.metrics.health.HealthModule;

@Singleton
public class HealthController {
    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);
    private final HealthCheckRegistry registry;
    private final ExecutorService executor;

    @GuardedBy("this")
    private final Map<String, Result> failingChecks = Maps.newHashMap();

    @Inject
    HealthController(HealthCheckRegistry registry, @Named(HealthModule.HEALTH_CHECK_POOL_NAME) ExecutorService executor) {
        this.registry = registry;
        this.executor = executor;
    }

    public synchronized Map<String, Result> runHealthChecks() {
        SortedMap<String, Result> results = registry.runHealthChecks(executor);

        results.forEach((name, result) -> {
            final Result oldResult = failingChecks.get(name);
            if (result.isHealthy() && oldResult != null) {
                failingChecks.remove(name);
                LOG.info("Health check {} is now {}", name, result);
            } else if (!result.isHealthy() && (oldResult == null || !result.getMessage().equals(oldResult.getMessage()))) {
                failingChecks.put(name, result);
                if (result.getError() == null) {
                    LOG.error("Health check {} is now {}", name, result);
                } else {
                    LOG.error("Health check {} is now {}", name, result, result.getError());
                }
            } else {
                LOG.trace("Health check {} is still {}", name, result);
            }
        });

        return results;
    }
}
