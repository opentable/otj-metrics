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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Snapshot;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

class HistogramResponse implements MonitorResponse
{
    private final String name;
    private final Histogram metric;

    HistogramResponse(String name, Histogram metric)
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
        final Snapshot snapshot = metric.getSnapshot();
        return fillSnapshotMonitors(ImmutableMap.<String, Object>builder(), snapshot).build();
    }

    static Builder<String, Object> fillSnapshotMonitors(
            ImmutableMap.Builder<String, Object> map, Snapshot snapshot)
    {
        return map.put("min", snapshot.getMin())
                .put("max", snapshot.getMax())
                .put("mean", snapshot.getMean())
                .put("stdDev", snapshot.getStdDev())
                .put("median", snapshot.getMedian())
                .put("tp75", snapshot.get75thPercentile())
                .put("tp95", snapshot.get95thPercentile())
                .put("tp98", snapshot.get98thPercentile())
                .put("tp99", snapshot.get99thPercentile())
                .put("tp999", snapshot.get999thPercentile());
    }
}
