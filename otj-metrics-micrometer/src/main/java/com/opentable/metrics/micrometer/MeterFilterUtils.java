/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
