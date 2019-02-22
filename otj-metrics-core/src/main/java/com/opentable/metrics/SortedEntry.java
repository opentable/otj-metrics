package com.opentable.metrics;

import java.util.Map;
import java.util.TreeMap;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ComparisonChain;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

import com.opentable.metrics.http.HealthController;

public class SortedEntry implements Comparable<SortedEntry> {
    final String name;
    final HealthCheck.Result result;

    public static Map<SortedEntry, HealthCheck.Result> render(boolean all, Map<String, HealthCheck.Result> raw) {
        Map<SortedEntry, HealthCheck.Result> rendered = new TreeMap<>();
        raw.forEach((name, result) -> {
            rendered.put(new SortedEntry(ClassUtils.getAbbreviatedName(name, 20), result), result);
        });

        if (!all && !rendered.isEmpty()) {
            HealthCheck.Result worstResult = rendered.keySet().iterator().next().getResult();
            if (!worstResult.isHealthy()) {
                rendered.keySet().removeIf(e -> e.getResult().isHealthy());
            }
        }

        return rendered;
    }


    public SortedEntry(String name, HealthCheck.Result result) {
        this.name = name;
        this.result = result;
    }

    @Override
    public int compareTo(SortedEntry o) {
        return ComparisonChain.start()
                // severity descending
                .compare(o.result, result, HealthController::compare)
                // name ascending
                .compare(name, o.name)
                .result();
    }


    public HealthCheck.Result getResult() {
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
