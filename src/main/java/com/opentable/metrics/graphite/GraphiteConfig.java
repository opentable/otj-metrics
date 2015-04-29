package com.opentable.metrics.graphite;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.DefaultNull;

/**
 * Created by jeremy on 4/16/15.
 */
interface GraphiteConfig {

    @Config("ot.graphite.graphite-host")
    @DefaultNull
    String getGraphiteHost();

    @Config("ot.graphite.graphite-port")
    @Default("2003")
    int getGraphitePort();

    @Config("ot.graphite.reporting-period-in-seconds")
    @Default("10")
    int getReportingPeriodInSeconds();
}
