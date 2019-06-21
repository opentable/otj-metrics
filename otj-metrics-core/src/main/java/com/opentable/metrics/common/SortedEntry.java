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

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ComparisonChain;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

import com.opentable.metrics.http.HealthController;
import com.opentable.metrics.ready.ReadyController;
import com.opentable.metrics.ready.Result;

/**
 * Common code for both Ready and Health checks.
 * @param <T> Ready.Result or HealthCheck.Result
 */
public class SortedEntry<T> implements Comparable<SortedEntry<T>> {
    private static final Predicate<Result> READY_CHECK_PREDICATE = Result::isReady;
    private static final Predicate<HealthCheck.Result> HEALTH_CHECK_PREDICATE = HealthCheck.Result::isHealthy;
    private static final Comparator<Result> READY_CHECK_COMPARATOR = ReadyController::compare;
    private static final Comparator<HealthCheck.Result> HEALTH_CHECK_COMPARATOR = HealthController::compare;

    private final String name;
    private final T result;
    private final Comparator<T> comparator;

    public static Map<SortedEntry<Result>, Result> ready(boolean all, Map<String, Result> raw) {
        return render(all, raw, READY_CHECK_PREDICATE, READY_CHECK_COMPARATOR);
    }

    public static Map<SortedEntry<HealthCheck.Result>, HealthCheck.Result> health(boolean all, Map<String, HealthCheck.Result> raw) {
        return render(all, raw, HEALTH_CHECK_PREDICATE, HEALTH_CHECK_COMPARATOR);
    }

    private static <U> Map<SortedEntry<U>, U> render(boolean all, Map<String, U> raw, Predicate<U> test, Comparator<U> comparator) {
        Map<SortedEntry<U>, U> rendered = new TreeMap<>();
        raw.forEach((name, result) -> {
            rendered.put(new SortedEntry<U>(ClassUtils.getAbbreviatedName(name, 20 ), result, comparator), result);
        });

        if (!all && !rendered.isEmpty()) {
            U worstResult = rendered.keySet().iterator().next().getResult();
            if (!test.test(worstResult)) {
                rendered.keySet().removeIf(e -> test.test(e.getResult()));
            }
        }
        return rendered;
    }


    public SortedEntry(String name, T result, Comparator<T> comparator) {
        this.name = name;
        this.result = result;
        this.comparator = comparator;
    }

    @Override
    public int compareTo(SortedEntry<T> o) {
        return ComparisonChain.start()
                // severity descending
                .compare(o.result, result, comparator)
                // name ascending
                .compare(name, o.name)
                .result();
    }


    public T getResult() {
        return result;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj, false);
    }

    @Override
    @JsonValue
    public String toString() {
        return name;
    }

}
