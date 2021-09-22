package com.opentable.metrics.actuate.micrometer;

import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import com.opentable.httpheaders.OTHeaders;

@Import(OtMicrometerConfig.class)
@ConditionalOnProperty(prefix = OtMicrometerConfig.CONFIGURATION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class OtWebMvcTagsContributor implements WebMvcTagsContributor {
    private static final String REFERRING_SERVICE_TAG_NAME = "referring-service";
    private static final String UNKNOWN = "unknown";

    private final List<String> trackableReferringServices;

    @Inject
    public OtWebMvcTagsContributor(OtMicrometerConfig otMicrometerConfig) {
        trackableReferringServices = otMicrometerConfig.getReferringServiceTracking();
    }

    @Override
    public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
        final String referringServiceHeader = request.getHeader(OTHeaders.REFERRING_SERVICE);
        final String referringService = isTrackable(referringServiceHeader) ? referringServiceHeader : UNKNOWN;
        return Tags.empty().and(Tag.of(REFERRING_SERVICE_TAG_NAME, referringService));
    }

    @Override
    public Iterable<Tag> getLongRequestTags(HttpServletRequest request, Object handler) {
        return null;
    }

    private boolean isTrackable(String referringServiceHeader) {
        return StringUtils.hasText(referringServiceHeader) && trackableReferringServices.contains(referringServiceHeader);
    }
}
