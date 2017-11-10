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
package com.opentable.metrics.http;

import java.util.Map;

import com.codahale.metrics.Metered;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

class MeterResponse implements MonitorResponse
{
    private final String name;
    private final Metered metric;

    MeterResponse(String name, Metered metric)
    {
        this.name = name;
        this.metric = metric;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Map<String, Object> getMonitors()
    {
        return fillMeterMonitors(ImmutableMap.<String, Object>builder()).build();
    }

    protected Builder<String, Object> fillMeterMonitors(ImmutableMap.Builder<String, Object> builder)
    {
        return builder.put("count", metric.getCount())
            .put("meanRate", metric.getMeanRate())
            .put("oneMinuteRate", metric.getOneMinuteRate())
            .put("fiveMinuteRate", metric.getFiveMinuteRate())
            .put("fifteenMinuteRate", metric.getFifteenMinuteRate());
    }
}
