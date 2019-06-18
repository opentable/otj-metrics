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
package com.opentable.metrics.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import com.google.common.collect.Maps;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.env.ConfigurableEnvironment;

import com.opentable.metrics.http.CheckState;
import com.opentable.spring.PropertySourceUtil;

/**
 * Common Code for both ReadyController and HealthController
 * @param <T>
 */
public abstract class CheckController<T> {

    protected Map<String, T> failingChecks = new ConcurrentHashMap<>();
    protected final Map<String, Set<String>> groups = new HashMap<>();

    protected final ExecutorService executor;

    public CheckController(ExecutorService executor,
                           ConfigurableEnvironment env, String configPrefix) {
        this.executor = executor;
        final Properties groupBaseConf = PropertySourceUtil.getProperties(env, configPrefix);
        groupBaseConf.stringPropertyNames().forEach(group -> {
            final Set<String> groupItems = Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(groupBaseConf.getProperty(group).split(","))));
            groups.put(group, groupItems);
        });
    }

    public Pair<Map<String, T>, CheckState> runChecks() {
        final SortedMap<String, T> checkResults = getCheckResults();
        final CheckState state = checkResults.values().stream()
                .map(this::resultToState)
                .max(CheckState.SEVERITY)
                .orElse(CheckState.HEALTHY);
        return Pair.of(checkResults, state);
    }

    public Pair<Map<String, T>, CheckState> runChecks(String group) {
        final Set<String> groupItems = groups.get(group);
        if (groupItems == null) {
            return null;
        }
        final SortedMap<String, T> checkResults = getCheckResults();
        final CheckState state = checkResults.keySet().stream()
                .filter(groupItems::contains)
                .map(checkResults::get)
                .map(this::resultToState)
                .max(CheckState.SEVERITY)
                .orElse(CheckState.HEALTHY);
        final Map<String, T> toReturn = Maps.filterKeys(checkResults, groupItems::contains);
        return Pair.of(toReturn, state);
    }

    protected abstract CheckState resultToState(T r);
    protected abstract SortedMap<String, T> getCheckResults();

}
