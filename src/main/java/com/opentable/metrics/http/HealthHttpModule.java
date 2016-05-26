package com.opentable.metrics.http;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class HealthHttpModule extends AbstractModule {
    @Override
    protected void configure() {
        bind (HealthController.class).in(Scopes.SINGLETON);
        bind (HealthResource.class);
    }
}
