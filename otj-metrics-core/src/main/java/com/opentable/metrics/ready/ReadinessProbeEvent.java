package com.opentable.metrics.ready;

import org.springframework.context.ApplicationEvent;

public class ReadinessProbeEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;

    private final boolean ready;
    public ReadinessProbeEvent(final Object source, boolean ready) {
        super(source);
        this.ready = ready;
    }

    public boolean isReady() {
        return ready;
    }
}
