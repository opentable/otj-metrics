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

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.jvm.Memory;

/**
 * If JVM argument {@code -XX:NativeMemoryTracking=summary} is present, will register NMT-related metrics.
 * If not, won't do anything.
 *
 */
public class NmtMetrics {
    private static final Logger LOG = LoggerFactory.getLogger(NmtMetrics.class);
    private static final Duration REFRESH_PERIOD = Duration.ofSeconds(10);

    private final String prefix;
    private final MetricRegistry metrics;

    private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(
        1,
        new ThreadFactoryBuilder()
            .setNameFormat("nmt-fetcher-%d")
            .setDaemon(true)
            .build()
    );

    private final ConcurrentMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public NmtMetrics(final String metricNamePrefix, final MetricRegistry metrics) {
        prefix = metricNamePrefix;
        this.metrics = metrics;
    }

    public synchronized void register() {
        exec.scheduleAtFixedRate(this::poll, 0, REFRESH_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void setGaugeValue(String metricName, long value) {
        AtomicLong gauge = this.gauges.get(metricName);
        if (gauge == null) {
            AtomicLong newGauge = new AtomicLong(value);
            gauge = this.gauges.putIfAbsent(metricName, newGauge);
            if (gauge == null) {
                metrics.register(metricName, (Gauge<Long>) newGauge::longValue);
                return;
            }
        }
        gauge.set(value);
    }

    private void poll() {
        try {
            Optional.ofNullable(Memory.getNmt())
                .map(i -> i.categories)
                .orElse(Collections.emptyMap())
                .forEach((key, value) -> {
                    final String fullMetricName = prefix + "." + key.toLowerCase(Locale.ROOT).replaceAll(" ", "-");
                    setGaugeValue(fullMetricName + ".reserved", value.reserved);
                    setGaugeValue(fullMetricName + ".committed", value.committed);
                });
        } catch (RuntimeException r) {
            LOG.error("Error polling NMT metrics", r);
        }
    }
}
