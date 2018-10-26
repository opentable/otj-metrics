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
package com.opentable.metrics.http;

import java.util.Map;
import java.util.TreeMap;

import com.codahale.metrics.health.HealthCheck.Result;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ComparisonChain;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthEndpoint {

    @Autowired
    private HealthController controller;

    @GetMapping
    public ResponseEntity<Map<SortedEntry, Result>> getHealth(@RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks();
        return ResponseEntity.status(result.getRight().getHttpStatus()).body(render(all, result.getLeft()));
    }

    @GetMapping("/group/{group}")
    public ResponseEntity<?> getHealthGroup(@PathVariable("group") String group, @RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runHealthChecks(group);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(result.getRight().getHttpStatus()).body(render(all, result.getLeft()));
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
