package com.opentable.metrics.http;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.config.Config;
import com.opentable.metrics.health.HealthConfiguration;

@Singleton
public class HealthController {
    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);
    private final HealthCheckRegistry registry;
    private final ExecutorService executor;

    @GuardedBy("this")
    private final Map<String, Result> failingChecks = Maps.newHashMap();

    private static final String CONFIG_PREFIX = "ot.metrics.health.group.";

    private final Map<String,Set<String>> groups;

    @Inject
    HealthController(HealthCheckRegistry registry, @Named(HealthConfiguration.HEALTH_CHECK_POOL_NAME) ExecutorService executor,
            Config config) {
        this.registry = registry;
        this.executor = executor;
        final AbstractConfiguration groupBaseConf = config.getConfiguration(CONFIG_PREFIX);
        groupBaseConf.setListDelimiter(',');
        final Iterator iterator = groupBaseConf.getKeys();
        this.groups = new HashMap<>();
        while(iterator.hasNext()) {
            String group = iterator.next().toString();
            Set<String> groupItems = ImmutableSet.copyOf(groupBaseConf.getStringArray(group));
            groups.put(group, groupItems);
        }
    }

    public Pair<Map<String, Result>, Boolean> runHealthChecks() {
        final SortedMap<String, Result> checkResults = getCheckResults();
        final boolean anyFailures = checkResults.values().stream()
                .anyMatch(result -> !result.isHealthy());
        return Pair.of(checkResults, !anyFailures);
    }

    public Pair<Map<String, Result>, Boolean> runHealthChecks(String group) {
        final Set<String> groupItems = groups.get(group);
        if (groupItems == null) {
            return null;
        }
        final SortedMap<String, Result> checkResults = getCheckResults();
        final boolean anyFailures = checkResults.keySet().stream()
                .filter(groupItems::contains)
                .map(checkResults::get)
                .anyMatch(r -> !r.isHealthy());
        final Map<String, Result> toReturn = Maps.filterKeys(checkResults, groupItems::contains);
        return Pair.of(toReturn, !anyFailures);
    }

    private SortedMap<String, Result> getCheckResults() {
        final SortedMap<String, Result> results = registry.runHealthChecks(executor);

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
