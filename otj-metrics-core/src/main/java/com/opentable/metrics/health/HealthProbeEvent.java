package com.opentable.metrics.health;

import org.springframework.context.ApplicationEvent;

public class HealthProbeEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;

    private final boolean healthy;
    public HealthProbeEvent(final Object source, boolean healthy) {
        super(source);
        this.healthy = healthy;
    }

    public boolean isHealthy() {
        return healthy;
    }
}
