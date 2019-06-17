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

import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;

import com.opentable.metrics.http.CheckState;
import com.opentable.spring.PropertySourceUtil;

@Named
public class ReadyController {
    private static final Logger LOG = LoggerFactory.getLogger(ReadyController.class);
    private static final String CONFIG_PREFIX = "ot.metrics.ready.group.";
    private static final String WARN_PREFIX = "WARN: ";

    private final Map<String, ReadyCheck.Result> failingChecks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groups = new HashMap<>();

    private final ReadyCheckRegistry registry;
    private final ExecutorService executor;

    public ReadyController(ReadyCheckRegistry registry, @Named(ReadyConfiguration.READY_CHECK_POOL_NAME) ExecutorService executor,
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

    public Pair<Map<String, ReadyCheck.Result>, CheckState> runReadyChecks() {
        final SortedMap<String, ReadyCheck.Result> checkResults = getCheckResults();
        final CheckState state = checkResults.values().stream()
                .map(ReadyController::resultToState)
                .max(CheckState.SEVERITY)
                .orElse(CheckState.HEALTHY);
        return Pair.of(checkResults, state);
    }

    public Pair<Map<String, ReadyCheck.Result>, CheckState> runReadyChecks(String group) {
        final Set<String> groupItems = groups.get(group);
        if (groupItems == null) {
            return null;
        }
        final SortedMap<String, ReadyCheck.Result> checkResults = getCheckResults();
        final CheckState state = checkResults.keySet().stream()
                .filter(groupItems::contains)
                .map(checkResults::get)
                .map(ReadyController::resultToState)
                .max(CheckState.SEVERITY)
                .orElse(CheckState.HEALTHY);
        final Map<String, ReadyCheck.Result> toReturn = Maps.filterKeys(checkResults, groupItems::contains);
        return Pair.of(toReturn, state);
    }

    private static CheckState resultToState(ReadyCheck.Result r) {
        if (r.isReady()) {
            return CheckState.HEALTHY;
        }
        if (StringUtils.startsWithIgnoreCase(r.getMessage(), WARN_PREFIX)) {
            return CheckState.WARNING;
        }
        return CheckState.CRITICAL;
    }

    private SortedMap<String, ReadyCheck.Result> getCheckResults() {
        final SortedMap<String, ReadyCheck.Result> results = registry.runReadyChecks(executor);
        LOG.trace("The results gathered {}", results);
        results.forEach((name, result) -> {
            final ReadyCheck.Result oldResult = failingChecks.get(name);
            LOG.trace("oldResult vs currentResult: {} VS {}", oldResult, result);
            LOG.trace("currentState of failingChecks: {} ", failingChecks);
            LOG.trace("result.isReady, result.getMessage(), oldResult != null, oldResult.getMessage {} || {} || {} || {}", result.isReady(),
                    result.getMessage(), oldResult != null, oldResult == null ? "I can't tell you!" : oldResult.getMessage());
            if (result.isReady() && oldResult != null) {
                failingChecks.remove(name);
                LOG.info("Ready check {} is now {}", name, result);
            } else if (!result.isReady() && (oldResult == null || !Objects.equals(result.getMessage(), oldResult.getMessage()))) {
                failingChecks.put(name, result);
                if (result.getError() == null) {
                    LOG.error("Ready check {} is now {}", name, result);
                } else {
                    LOG.error("ready check {} is now {}", name, result, result.getError());
                }
            } else {
                LOG.trace("ready check {} is still {}", name, result);
            }
        });
        return results;
    }

    /** Utility to sort Result objects by severity. */
    public static int compare(ReadyCheck.Result r1, ReadyCheck.Result r2) {
        return resultToState(r1).compareTo(resultToState(r2));
    }
}
