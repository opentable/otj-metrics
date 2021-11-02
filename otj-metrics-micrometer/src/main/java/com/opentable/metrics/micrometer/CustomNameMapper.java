package com.opentable.metrics.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public class CustomNameMapper implements HierarchicalNameMapper {

    private final String prefix;

    public CustomNameMapper(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {

        return prefix + "." + id.getConventionName(convention);
    }
}
