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
package com.opentable.metrics.health;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Named;

import com.codahale.metrics.health.HealthCheck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * This health check does an OK job of reporting whether the application is healthy.  It flips to being healthy when
 * the Spring application is all wired up (when the context is refreshed), and becomes unhealthy when closed.
 * There are some caveats&mdash;see the comments in the source.
 */
@Named("mediocreHealthCheck")
class MediocreHealthCheck extends HealthCheck {
    private static final Logger LOG = LoggerFactory.getLogger(MediocreHealthCheck.class);

    /**
     * This bean is implicitly in singleton scope, so the healthy state will persist.
     */
    private final AtomicReference<State> state = new AtomicReference<>(new State(false, null));

    @Override
    protected HealthCheck.Result check() {
        return state.get().makeResult();
    }

    private void setHealthy(final boolean healthy, final ApplicationContextEvent event) {
        LOG.info("setting healthy {}; cause: {}", healthy, event);
        state.set(new State(healthy, event));
    }

    // Refreshed comes in as soon as everything is wired up, but closed doesn't have analogous sequencing.
    // Specifying an ordering doesn't help.  Specifying a shutdown hook still wouldn't enable us to reliably
    // get in ahead of other components.
    // TODO Improve.

    @EventListener
    public void refreshed(final ContextRefreshedEvent event) {
        setHealthy(true, event);
    }

    @EventListener
    public void closed(final ContextClosedEvent event) {
        setHealthy(false, event);
    }

    private static class State {
        private final boolean healthy;
        private final ApplicationContextEvent cause;
        State(final boolean healthy, final ApplicationContextEvent cause) {
            this.healthy = healthy;
            this.cause = cause;
        }
        private String formatMessage() {
            final String formatCause;
            if (cause == null) {
                formatCause = "initialized to unhealthy";
            } else {
                formatCause = cause.getClass().getSimpleName() + " @ " + Instant.ofEpochMilli(cause.getTimestamp());
            }
            return formatCause;
        }
        private HealthCheck.Result makeResult() {
            return healthy ? HealthCheck.Result.healthy(formatMessage()) :
                    HealthCheck.Result.unhealthy(formatMessage());
        }
    }
}
