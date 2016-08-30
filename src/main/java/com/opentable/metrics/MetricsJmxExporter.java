package com.opentable.metrics;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.management.MBeanServer;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MetricsJmxExporter {
    private final MBeanServer mbs;
    private final MetricRegistry metrics;
    private JmxReporter reporter;

    @Inject
    public MetricsJmxExporter(MBeanServer mbs, MetricRegistry metrics) {
        this.mbs = mbs;
        this.metrics = metrics;
    }

    @PostConstruct
    public void start() {
        reporter = JmxReporter.forRegistry(metrics).registerWith(mbs).build();
        reporter.start();
        LoggerFactory.getLogger(MetricsJmxExporter.class).info("Exported metrics to JMX");
    }

    @PreDestroy
    public void close() {
        reporter.close();
    }
}
