package com.opentable.metrics.graphite;

public enum DWMetricsToFilter {
    JVM("jvm"),
    JETTY("jetty"),
    WEB_MVC("http-server");

    String metricPathId;

    DWMetricsToFilter(String metricPathId) {
        this.metricPathId = metricPathId;
    }

    /**
     * If this string is present in the metric path, it will be filtered
     * @return String
     */
    public String getMetricPathId() {
        return this.metricPathId;
    }
}
