package com.opentable.metrics.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public class CustomNameMapper implements HierarchicalNameMapper {

    private final String prefix;

    public CustomNameMapper(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {

        StringBuilder hierarchicalName = new StringBuilder();

        hierarchicalName.append(prefix);
        hierarchicalName.append(".");
        hierarchicalName.append(id.getConventionName(convention));
        for (Tag tag : id.getTagsAsIterable()) {
            hierarchicalName.append('.')
                    .append(convention.tagValue(tag.getValue()));
        }
        return hierarchicalName.toString();
    }
}
