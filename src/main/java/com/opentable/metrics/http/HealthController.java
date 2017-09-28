package com.opentable.metrics.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.inject.Named;

import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;

import com.opentable.metrics.health.HealthConfiguration;
import com.opentable.spring.PropertySourceUtil;

@Named
public class HealthController {
    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);
    private static final String CONFIG_PREFIX = "ot.metrics.health.group.";
    private static final String WARN_PREFIX = "WARN: ";

    private final Map<String, Result> failingChecks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groups = new HashMap<>();

    private final HealthCheckRegistry registry;
    private final ExecutorService executor;

    HealthController(HealthCheckRegistry registry, @Named(HealthConfiguration.HEALTH_CHECK_POOL_NAME) ExecutorService executor,
            ConfigurableEnvironment env) {
        this.registry = registry;
        this.executor = executor;
        final Properties groupBaseConf = PropertySourceUtil.getProperties(env, CONFIG_PREFIX);
        groupBaseConf.stringPropertyNames().forEach(group -> {
            final Set<String> groupItems = Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(groupBaseConf.getProperty(group).split(","))));
            groups.put(group, groupItems);
        });
    }

    public Pair<Map<String, Result>, CheckState> runHealthChecks() {
        final SortedMap<String, Result> checkResults = getCheckResults();
        final CheckState state = checkResults.values().stream()
                .map(HealthController::resultToState)
                .max(CheckState.SEVERITY)
                .orElse(CheckState.HEALTHY);
        return Pair.of(checkResults, state);
    }

    public Pair<Map<String, Result>, CheckState> runHealthChecks(String group) {
        final Set<String> groupItems = groups.get(group);
        if (groupItems == null) {
            return null;
        }
        final SortedMap<String, Result> checkResults = getCheckResults();
        final CheckState state = checkResults.keySet().stream()
                .filter(groupItems::contains)
                .map(checkResults::get)
                .map(HealthController::resultToState)
                .max(CheckState.SEVERITY)
                .orElse(CheckState.HEALTHY);
        final Map<String, Result> toReturn = Maps.filterKeys(checkResults, groupItems::contains);
        return Pair.of(toReturn, state);
    }

    private static CheckState resultToState(Result r) {
        if (r.isHealthy()) {
            return CheckState.HEALTHY;
        }
        if (StringUtils.startsWithIgnoreCase(r.getMessage(), WARN_PREFIX)) {
            return CheckState.WARNING;
        }
        return CheckState.CRITICAL;
    }

    private SortedMap<String, Result> getCheckResults() {
        final SortedMap<String, Result> results = registry.runHealthChecks(executor);
        LOG.trace("The resullts gathered {}", results);
        results.forEach((name, result) -> {
            final Result oldResult = failingChecks.get(name);
            LOG.trace("oldResult vs currentResult: {} VS {}", oldResult, result);
            LOG.trace("currentState of failingChecks: ", failingChecks);
            LOG.trace("result.isHealthy, result.getMessage(), oldResult != null, oldResult.getMessage {} || {} || {} || {}", result.isHealthy(),
                    result.getMessage(), oldResult != null, oldResult == null ? "I can't tell you!" : oldResult.getMessage());
            if (result.isHealthy() && oldResult != null) {
                failingChecks.remove(name);
                LOG.info("Health check {} is now {}", name, result);
            } else if (!result.isHealthy() && (oldResult == null || !Objects.equals(result.getMessage(), oldResult.getMessage()))) {
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

    /** Utility to sort Result objects by severity. */
    public static int compare(Result r1, Result r2) {
        return resultToState(r1).compareTo(resultToState(r2));
    }
}
