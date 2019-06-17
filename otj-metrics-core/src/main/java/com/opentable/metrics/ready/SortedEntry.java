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
package com.opentable.metrics.ready;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ComparisonChain;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

public class SortedEntry implements Comparable<SortedEntry> {
    final String name;
    final ReadyCheck.Result result;

    public static Map<SortedEntry, ReadyCheck.Result> render(boolean all, Map<String, ReadyCheck.Result> raw) {
        Map<SortedEntry, ReadyCheck.Result> rendered = new TreeMap<>();
        raw.forEach((name, result) -> {
            rendered.put(new SortedEntry(ClassUtils.getAbbreviatedName(name, 20), result), result);
        });

        if (!all && !rendered.isEmpty()) {
            ReadyCheck.Result worstResult = rendered.keySet().iterator().next().getResult();
            if (!worstResult.isReady()) {
                rendered.keySet().removeIf(e -> e.getResult().isReady());
            }
        }

        return rendered;
    }


    public SortedEntry(String name, ReadyCheck.Result result) {
        this.name = name;
        this.result = result;
    }

    @Override
    public int compareTo(SortedEntry o) {
        return ComparisonChain.start()
                // severity descending
                .compare(o.result, result, ReadyController::compare)
                // name ascending
                .compare(name, o.name)
                .result();
    }


    public ReadyCheck.Result getResult() {
        return result;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj, false);
    }

    @Override
    @JsonValue
    public String toString() {
        return name;
    }

}
