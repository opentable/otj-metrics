package com.opentable.metrics.graphite;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.spring.ConversionServiceConfiguration;

@Configuration
@Import({
        /**
         * {@link ConversionServiceConfiguration} needed for {@link java.time.Duration} config value.
         */
        ConversionServiceConfiguration.class,
        GraphiteReporterWrapper.class,
})
public class GraphiteConfiguration {}
