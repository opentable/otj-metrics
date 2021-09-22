package com.opentable.metrics.actuate.micrometer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(OtMicrometerConfig.CONFIGURATION_PREFIX)
@ConditionalOnProperty(prefix = OtMicrometerConfig.CONFIGURATION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class OtMicrometerConfig {

    static final String CONFIGURATION_PREFIX = "management.metrics.export.dw-new";

    private List<String> referringServiceTracking = Collections.emptyList();
    private String prefix = "v2";

    public List<String> getReferringServiceTracking() {
        return new ArrayList<>(referringServiceTracking);
    }

    public void setReferringServiceTracking(List<String> trackableReferringServices) {
        this.referringServiceTracking = trackableReferringServices;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
