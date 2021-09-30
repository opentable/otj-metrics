package com.opentable.metrics.actuate.micrometer;

import javax.inject.Inject;

import org.springframework.boot.actuate.metrics.web.reactive.server.WebFluxTagsContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import com.opentable.httpheaders.OTHeaders;

@Import(OtMicrometerConfig.class)
@ConditionalOnProperty(prefix = OtMicrometerConfig.CONFIGURATION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class OtTagsContributorWebFlux extends OtTagsContributorCommon implements WebFluxTagsContributor {

    @Inject
    public OtTagsContributorWebFlux(OtMicrometerConfig otMicrometerConfig) {
        super(otMicrometerConfig);
    }

    @Override
    public Iterable<Tag> httpRequestTags(ServerWebExchange exchange, Throwable ex) {
        final String referringServiceHeader = exchange.getRequest().getHeaders().getFirst(OTHeaders.REFERRING_SERVICE);
        return Tags.empty().and(Tag.of(REFERRING_SERVICE_TAG_NAME, getTagValue(referringServiceHeader)));
    }
}
