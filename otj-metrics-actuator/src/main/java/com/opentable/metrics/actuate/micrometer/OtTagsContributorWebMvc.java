package com.opentable.metrics.actuate.micrometer;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import com.opentable.httpheaders.OTHeaders;

/**
 * WebMVC tags contributor. Adds referring service name as a tag.
 */
@Import(OtMicrometerConfig.class)
@ConditionalOnProperty(prefix = OtMicrometerConfig.CONFIGURATION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class OtTagsContributorWebMvc extends OtTagsContributorCommon implements WebMvcTagsContributor {

    @Inject
    public OtTagsContributorWebMvc(OtMicrometerConfig otMicrometerConfig) {
        super(otMicrometerConfig);
    }

    @Override
    public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
        final String referringServiceHeader = request.getHeader(OTHeaders.REFERRING_SERVICE);
        return Tags.empty().and(Tag.of(REFERRING_SERVICE_TAG_NAME, getTagValue(referringServiceHeader)));
    }

    @Override
    public Iterable<Tag> getLongRequestTags(HttpServletRequest request, Object handler) {
        return null;
    }


}
