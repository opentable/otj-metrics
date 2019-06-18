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
package com.opentable.metrics.mvc;

import java.util.Map;

import com.codahale.metrics.health.HealthCheck.Result;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opentable.metrics.health.HealthConfiguration;
import com.opentable.metrics.http.CheckState;
import com.opentable.metrics.http.HealthController;
import com.opentable.metrics.common.SortedEntry;

@RestController
@RequestMapping(HealthConfiguration.HEALTH_CHECK_PATH)
public class HealthEndpoint {

    @Autowired
    private HealthController controller;

    @GetMapping
    public ResponseEntity<Map<SortedEntry<Result>, Result>> getHealth(@RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runChecks();
        return ResponseEntity.status(result.getRight().getHttpStatus()).body(SortedEntry.health(all, result.getLeft()));
    }

    @GetMapping("/group/{group}")
    public ResponseEntity<?> getHealthGroup(@PathVariable("group") String group, @RequestParam(name="all", defaultValue="false") boolean all) {
        final Pair<Map<String, Result>, CheckState> result = controller.runChecks(group);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(result.getRight().getHttpStatus()).body(SortedEntry.health(all, result.getLeft()));
    }
}
