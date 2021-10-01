package com.opentable.metrics.actuate.micrometer;

import java.util.List;

import org.springframework.util.StringUtils;

/**
 * Base class for tags contributors. Contains utility methods and configuration.
 */
class OtTagsContributorCommon {
    static final String REFERRING_SERVICE_TAG_NAME = "referring-service";
    private static final String UNKNOWN = "unknown";

    private final List<String> trackableReferringServices;

    OtTagsContributorCommon(OtMicrometerConfig otMicrometerConfig) {
        trackableReferringServices = otMicrometerConfig.getReferringServiceTracking();
    }

    protected String getTagValue(String referringServiceHeader) {
        return isTrackable(referringServiceHeader) ? referringServiceHeader : UNKNOWN;
    }

    private boolean isTrackable(String referringServiceHeader) {
        return StringUtils.hasText(referringServiceHeader) && trackableReferringServices.contains(referringServiceHeader);
    }
}
