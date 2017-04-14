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
    protected Result check() {
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
        private Result makeResult() {
            return healthy ? Result.healthy(formatMessage()) : Result.unhealthy(formatMessage());
        }
    }
}
