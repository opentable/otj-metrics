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
    private void postConstruct() {
        checks.forEach(registry::register);
    }

    @PreDestroy
    private void preDestroy() {
        checks.keySet().forEach(registry::unregister);
    }
}
