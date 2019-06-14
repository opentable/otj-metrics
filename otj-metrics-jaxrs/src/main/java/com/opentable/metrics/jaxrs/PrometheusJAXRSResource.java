package com.opentable.metrics.jaxrs;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

@Path("/infra/prometheus")
public class PrometheusJAXRSResource {
    private static final Logger LOG = LoggerFactory.getLogger(PrometheusJAXRSResource.class);

    private final CollectorRegistry collectorRegistry;

    @Inject
    public PrometheusJAXRSResource(CollectorRegistry collectorRegistry) {
        this.collectorRegistry = collectorRegistry;
    }

    @GET
    @Produces(TextFormat.CONTENT_TYPE_004)
    public Response getMetrics(@QueryParam("name")List<String> names) throws IOException {
        LOG.info("Called!");
        return commonCode(names);
    }

    @POST
    @Produces(TextFormat.CONTENT_TYPE_004)
    public Response postMetrics(@QueryParam("name")List<String> names) throws IOException {
        return commonCode(names);
    }

    private Response commonCode(final List<String> names) throws IOException {
        return Response.ok(writer(names)).build();
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
