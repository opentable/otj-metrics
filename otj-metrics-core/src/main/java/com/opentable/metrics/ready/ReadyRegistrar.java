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

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;


@Named
/**
 * Takes an injection of all ReadyChecks, and registers/deregisters them on start/stop
 */
class ReadyRegistrar {
    private final ReadyCheckRegistry registry;
    private final Map<String, ReadyCheck> checks;

    @Inject
    ReadyRegistrar(final ReadyCheckRegistry registry, final Map<String, ReadyCheck> checks) {
        this.registry = registry;
        this.checks = checks;
    }

    @PostConstruct
    public void postConstruct() {
        checks.forEach(registry::register);
    }

    @PreDestroy
    public void preDestroy() {
        checks.keySet().forEach(registry::unregister);
    }
}
