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
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        reporter.stop();
    }
}
