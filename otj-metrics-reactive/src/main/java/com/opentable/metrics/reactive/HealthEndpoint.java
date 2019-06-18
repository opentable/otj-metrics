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
package com.opentable.metrics.reactive;

import static com.codahale.metrics.health.HealthCheck.Result;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import com.opentable.metrics.health.HealthConfiguration;
import com.opentable.metrics.http.CheckState;
import com.opentable.metrics.http.HealthController;
import com.opentable.metrics.ready.SortedEntry;

/**
 * Health endpoint for Reactive HTTP servers.
 */
@RestController
@RequestMapping(HealthConfiguration.HEALTH_CHECK_PATH)
public class HealthEndpoint {

    private final HealthController controller;

    public HealthEndpoint(HealthController controller) {
        this.controller = controller;
    }

    @GetMapping
    public Mono<ResponseEntity<Map<SortedEntry<Result>, Result>>> getHealth(@RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runChecks();
        return Mono.just(ResponseEntity.status(result.getRight().getHttpStatus()).body(SortedEntry.health(all, result.getLeft())));
    }

    @GetMapping("/group/{group}")
    public Mono<ResponseEntity<?>> getHealthGroup(@PathVariable("group") String group, @RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runChecks(group);
        if (result == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.status(result.getRight().getHttpStatus()).body(SortedEntry.health(all, result.getLeft())));
    }

}
