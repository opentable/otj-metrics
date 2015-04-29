package com.opentable.metrics.graphite;

import com.google.inject.AbstractModule;
import com.opentable.config.ConfigBinder;

/**
 * Created by jeremy on 4/29/15.
 */
public class GraphiteModule extends AbstractModule {
    @Override
    protected void configure() {
        ConfigBinder.of(binder()).bind(GraphiteConfig.class);
        bind(GraphiteReporter.class).asEagerSingleton();
    }
}
