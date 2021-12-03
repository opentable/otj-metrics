/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
