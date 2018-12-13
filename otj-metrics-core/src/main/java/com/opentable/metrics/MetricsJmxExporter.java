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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.management.MBeanServer;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Exposes metric via JMX
 */
@Component
public class MetricsJmxExporter {
    private final MBeanServer mbs;
    private final MetricRegistry metrics;
    private JmxReporter reporter;

    /**
     * Create a Metrics JMX Exporter. Invoked by Spring.
     * @param mbs the mBean server to expose the metrics on
     * @param metrics the mtrics to expose
     */
    @Inject
    public MetricsJmxExporter(MBeanServer mbs, MetricRegistry metrics) {
        this.mbs = mbs;
        this.metrics = metrics;
    }

    /**
     * Create a {@link JmxReporter} to expose metrics via JMX
     */
    @PostConstruct
    public void start() {
        reporter = JmxReporter.forRegistry(metrics).registerWith(mbs).build();
        reporter.start();
        LoggerFactory.getLogger(MetricsJmxExporter.class).info("Exported metrics to JMX");
    }

    /**
     * Stop the {@link JmxReporter}
     */
    @PreDestroy
    public void close() {
        reporter.close();
    }
}
