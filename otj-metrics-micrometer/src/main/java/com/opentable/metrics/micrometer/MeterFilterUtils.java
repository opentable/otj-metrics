package com.opentable.metrics.micrometer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import java.util.List;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

public final class MeterFilterUtils {

    private MeterFilterUtils() {
        throw new UnsupportedOperationException();
    }

    static MeterFilter ignoreTags(String meterNamePrefix, String... tagKeys) {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {

                if (!id.getName().startsWith(meterNamePrefix)) {
                    return id;
                }

                List<Tag> tags = stream(id.getTagsAsIterable().spliterator(), false)
                        .filter(t -> {
                            for (String tagKey : tagKeys) {
                                if (t.getKey().equals(tagKey)) {
                                    return false;
                                }
                            }
                            return true;
                        }).collect(toList());

                return id.replaceTags(tags);
            }
        };
    }
}
