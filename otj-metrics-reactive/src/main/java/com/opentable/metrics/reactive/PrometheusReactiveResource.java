package com.opentable.metrics.reactive;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

@RequestMapping("/infra/prometheus")
@RestController
public class PrometheusReactiveResource {
    private static final Logger LOG = LoggerFactory.getLogger(PrometheusReactiveResource.class);

    private final CollectorRegistry collectorRegistry;

    @Inject
    public PrometheusReactiveResource(CollectorRegistry collectorRegistry) {
        this.collectorRegistry = collectorRegistry;
    }

    @GetMapping(produces = TextFormat.CONTENT_TYPE_004)
    public String getMetrics(@RequestParam("name")List<String> names) throws IOException {
        LOG.info("Called!");
        return commonCode(names);
    }

    @PostMapping(produces = TextFormat.CONTENT_TYPE_004)
    public String postMetrics(@RequestParam("name")List<String> names) throws IOException {
        return commonCode(names);
    }

    private String commonCode(final List<String> names) throws IOException {
        return writer(names);
    }

    private String writer(final List<String> names) throws IOException {
        StringWriter stringWriter = new StringWriter();
        TextFormat.write004(stringWriter, collectorRegistry.filteredMetricFamilySamples(parse(names)));
        String str = stringWriter.toString();
        LOG.info("Got {}", str);
        return str;
    }

    private Set<String> parse(List<String> names) {
        if (names == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(names);
    }

}
