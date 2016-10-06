package com.opentable.metrics.jvm;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.opentable.jvm.Memory;
import com.opentable.jvm.Nmt;

/**
 * If JVM argument {@code -XX:NativeMemoryTracking=summary} is present, will register NMT-related metrics.
 * If not, won't do anything.
 *
 * As the lifetime of a process goes on, NMT returns increasingly more categories of information when you query it.
 * This means that at the outset, we do not know the complete set of gauges to register.  Given how DropWizard works,
 * the lightest-weight opportunity we have to identify new metrics is when the gauge functions are called.
 * Furthermore, this is also the opportunity to ensure that we are reading the latest NMT values.  However, fetching
 * NMT information is non-trivial, so we want to rate-limit it.  Therefore, the design employed here is that on gauge
 * measurement, we check to update our NMT observations according to a refresh period.  Having refreshed the NMT
 * information, we can <em>also</em> check for further metrics to register.
 *
 * Cf. https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr022.html
 *
 * In terms of thread-safety, we assume the worst, and handle {@link #register()} and the gauge functions being called
 * by arbitrary threads.
 *
 * FindBugs is bad at understanding the synchronized access and modification of {@link #nmt}, particularly when
 * referred to in lambdas, so we suppress {@code IS2_INCONSISTENT_SYNC} warnings.
 */
@SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC",
        justification = "FindBugs gets confused by the lambda references to nmt.")
public class NmtMetrics {
    private static final Logger LOG = LoggerFactory.getLogger(NmtMetrics.class);
    private static final Duration REFRESH_PERIOD = Duration.ofSeconds(10);

    private final String prefix;
    private final MetricRegistry metrics;
    private final Set<String> registeredMetrics = new HashSet<>();

    private Nmt nmt;
    private Instant lastRefresh;

    public NmtMetrics(final String metricNamePrefix, final MetricRegistry metrics) {
        prefix = metricNamePrefix;
        this.metrics = metrics;
    }

    public synchronized void register() {
        nmt = Memory.getNmt();
        if (nmt == null) {
            LOG.info("got null NMT info; not registering any metrics");
            return;
        }
        lastRefresh = Instant.now();
        refreshCategories();
        if (registeredMetrics.isEmpty()) {
            LOG.warn("Didn't find any NMT metrics to register!");
        }
        registerGauges("total", () -> nmt.total);
    }

    private void refreshCategories() {
        final Set<String> atStart = ImmutableSet.copyOf(registeredMetrics);
        nmt.categories.keySet().forEach(categoryName -> {
            final String metricName = categoryName.toLowerCase().replaceAll(" ", "-");
            if (registeredMetrics.contains(metricName)) {
                return;
            }
            registerCategoryGauges(metricName, categoryName);
        });
        final SetView<String> added = Sets.difference(registeredMetrics, atStart);
        if (!added.isEmpty()) {
            LOG.debug("Registered metrics: {}", added);
        }
    }

    private void refreshNmt() {
        final Instant now = Instant.now();
        if (now.isBefore(lastRefresh.plus(REFRESH_PERIOD))) {
            return;
        }
        nmt = Memory.getNmt();
        Preconditions.checkNotNull(nmt);
        lastRefresh = now;
        refreshCategories();
    }

    private void registerGauges(final String metricName, final Supplier<Nmt.Usage> usageSupplier) {
        if (!registeredMetrics.add(metricName)) {
            throw new IllegalStateException(String.format("%s already registered", metricName));
        }

        final String fullMetricName = prefix + "." + metricName;

        metrics.register(fullMetricName + ".reserved", (Gauge<Long>)() -> {
            synchronized (this) {
                refreshNmt();
                return usageSupplier.get().reserved;
            }
        });

        metrics.register(fullMetricName + ".committed", (Gauge<Long>)() -> {
            synchronized (this) {
                refreshNmt();
                return usageSupplier.get().committed;
            }
        });
    }

    private void registerCategoryGauges(final String metricName, final String categoryName) {
        registerGauges(metricName, () -> nmt.categories.get(categoryName));
    }
}
