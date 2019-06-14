package com.opentable.metrics.prometheus;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import com.codahale.metrics.MetricRegistry;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;

public class BridgeDropWizard {
    private final DropwizardExports dropwizardExports;
    private final CollectorRegistry collectorRegistry;
    @Inject
    public BridgeDropWizard(MetricRegistry metricRegistry, CollectorRegistry collectorRegistry, SampleBuilder sampleBuilder) {
        this.dropwizardExports = new DropwizardExports(metricRegistry, sampleBuilder);
        this.collectorRegistry = collectorRegistry;
    }

    @PostConstruct
    public void start() {
        dropwizardExports.register(collectorRegistry);
    }
}
