package com.opentable.metrics.http;

import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

public class HealthHttpModule extends ServletModule {
    @Override
    protected void configureServlets() {
        bind (HealthController.class).in(Scopes.SINGLETON);
        bind (HealthResource.class);
    }
}
