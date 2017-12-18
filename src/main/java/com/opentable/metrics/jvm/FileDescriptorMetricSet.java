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
package com.opentable.metrics.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Map;
import java.util.function.LongSupplier;

import com.sun.management.UnixOperatingSystemMXBean;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.google.common.collect.ImmutableMap;

public class FileDescriptorMetricSet implements MetricSet {
    private static final Gauge<Long> NULL = () -> null;

    private final Map<String, Metric> metricMap;

    public FileDescriptorMetricSet() {
        final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof UnixOperatingSystemMXBean) {
            final UnixOperatingSystemMXBean bean = (UnixOperatingSystemMXBean) osBean;
            metricMap = ImmutableMap.of(
                    "open", caller(bean::getOpenFileDescriptorCount),
                    "max", caller(bean::getMaxFileDescriptorCount)
            );
        } else {
            metricMap = ImmutableMap.of(
                    "open", NULL,
                    "max", NULL
            );
        }
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return metricMap;
    }

    /**
     * It's unclear whether or not {@link com.sun.management.UnixOperatingSystemMXBean} methods are re-entrant,
     * so we play it safe and wrap calls with a lock.
     */
    private synchronized long call(final LongSupplier f) {
        return f.getAsLong();
    }

    private Gauge<Long> caller(final LongSupplier f) {
        return () -> call(f);
    }
}
