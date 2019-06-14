package com.opentable.metrics.prometheus;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import com.codahale.metrics.MetricRegistry;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;

public class BridgeDropWizard {
    private final DropwizardExports dropwizardExports;
    private final CollectorRegistry collectorRegistry;
    @Inject
    public BridgeDropWizard(MetricRegistry metricRegistry, CollectorRegistry collectorRegistry) {
        this.dropwizardExports = new DropwizardExports(metricRegistry);
        this.collectorRegistry = collectorRegistry;
    }

    @PostConstruct
    public void start() {
        dropwizardExports.register(collectorRegistry);
    }
}
