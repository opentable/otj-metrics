package com.opentable.metrics;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Binder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;

public class HealthCheckBinder
{
    public static LinkedBindingBuilder<HealthCheck> bind(Binder binder, String checkName)
    {
        return MapBinder.newMapBinder(binder, String.class, HealthCheck.class).addBinding(checkName);
    }
}
