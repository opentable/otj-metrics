package com.opentable.metrics.reactive;

import static com.codahale.metrics.health.HealthCheck.Result;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ComparisonChain;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import com.opentable.metrics.http.CheckState;
import com.opentable.metrics.http.HealthController;

/**
 * Health endpoint for Reactive HTTP servers.
 */
@RestController
@RequestMapping("/health")
public class HealthEndpoint {

    private final HealthController controller;

    public HealthEndpoint(HealthController controller) {
        this.controller = controller;
    }

    @GetMapping
    public Mono<ResponseEntity<Map<SortedEntry, Result>>> getHealth(@RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks();
        return Mono.just(ResponseEntity.status(result.getRight().getHttpStatus()).body(render(all, result.getLeft())));
    }

    @GetMapping("/group/{group}")
    public Mono<ResponseEntity<?>> getHealthGroup(@PathVariable("group") String group, @RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks(group);
        if (result == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.status(result.getRight().getHttpStatus()).body(render(all, result.getLeft())));
    }

    private Map<SortedEntry, Result> render(boolean all, Map<String, Result> raw) {
        Map<SortedEntry, Result> rendered = new TreeMap<>();
        raw.forEach((name, result) -> {
            rendered.put(new SortedEntry(ClassUtils.getAbbreviatedName(name, 20), result), result);
        });

        if (!all && !rendered.isEmpty()) {
            Result worstResult = rendered.keySet().iterator().next().result;
            if (!worstResult.isHealthy()) {
                rendered.keySet().removeIf(e -> e.result.isHealthy());
            }
        }

        return rendered;
    }

    static class SortedEntry implements Comparable<SortedEntry> {
        final String name;
        final Result result;

        SortedEntry(String name, Result result) {
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
}
