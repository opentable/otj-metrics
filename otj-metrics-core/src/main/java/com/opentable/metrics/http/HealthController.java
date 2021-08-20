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

import java.time.Duration;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;

import com.opentable.metrics.common.CheckController;
import com.opentable.metrics.health.HealthConfiguration;
import com.opentable.metrics.health.HealthProbeEvent;

@Named
public class HealthController extends CheckController<Result> {
    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);
    private static final String CONFIG_PREFIX = "ot.metrics.health.group.";

    private final HealthCheckRegistry registry;

    @Inject
    public HealthController(@Value("${ot.endpoint.health.publish:PT0.05S}") Duration publishDelay, HealthCheckRegistry registry, @Named(HealthConfiguration.HEALTH_CHECK_POOL_NAME) ExecutorService executor,
                            ConfigurableEnvironment env, ApplicationEventPublisher publisher) {
        super(publishDelay, executor, env, CONFIG_PREFIX, publisher);
        this.registry = registry;
    }

    @Override
    protected CheckState resultToState(Result r) {
        return resToState(r);
    }

    private static CheckState resToState(Result r) {
        if (r.isHealthy()) {
            return CheckState.HEALTHY;
        }
        if (StringUtils.startsWithIgnoreCase(r.getMessage(), WARN_PREFIX)) {
            return CheckState.WARNING;
        }
        return CheckState.CRITICAL;
    }

    @Override
    protected SortedMap<String, Result> getCheckResults() {
        final SortedMap<String, Result> results = registry.runHealthChecks(executor);
        LOG.trace("The results gathered {}", results);
        results.forEach((name, result) -> {
            final Result oldResult = failingChecks.get(name);
            LOG.trace("oldResult vs currentResult: {} VS {}", oldResult, result);
            LOG.trace("currentState of failingChecks: {} ", failingChecks);
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

    @Override
    protected ApplicationEvent getEvent(final boolean checkPasses) {
        return new HealthProbeEvent(this, checkPasses);
    }

    /** Utility to sort Result objects by severity. */
    public static int compare(Result r1, Result r2) {
        return resToState(r1).compareTo(resToState(r2));
    }
}
