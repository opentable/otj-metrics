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
package com.opentable.metrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;

import com.opentable.metrics.jvm.GcMemoryMetrics;

public class GcMemoryMetricsDemo {
    public static void main(final String[] args) {
        final MetricRegistry metricRegistry = new MetricRegistry();
        new GcMemoryMetrics("demo", metricRegistry);
        final ConsoleReporter reporter = ConsoleReporter
                .forRegistry(metricRegistry)
                .build();
        reporter.start(1, TimeUnit.SECONDS);
        while (true) {
            try {
                System.gc();
                Thread.sleep(Duration.ofSeconds(1).toMillis());
            } catch (@SuppressWarnings("unused") final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        reporter.stop();
        reporter.close();
    }
}
