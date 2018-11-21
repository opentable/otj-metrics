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
package com.opentable.metrics.http;

import javax.servlet.ServletContextListener;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.AdminServlet;
import com.codahale.metrics.servlets.MetricsServlet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsHttpCoreConfiguration {

    @Value("${ot.metrics.http.path:/metrics}")
    private String path;

    @Bean
    public ServletContextListener getMetricsContextListener(final MetricRegistry metrics) {
        return new MetricsContextListener(metrics);
    }

    @Bean
    public ServletRegistrationBean<AdminServlet> getMetricsServlet() {
        return new ServletRegistrationBean<>(new AdminServlet(), path + "/*");
    }

    private static class MetricsContextListener extends MetricsServlet.ContextListener {
        private final MetricRegistry metrics;

        MetricsContextListener(MetricRegistry metrics) {
            this.metrics = metrics;
        }

        @Override
        protected MetricRegistry getMetricRegistry() {
            return metrics;
        }
    }
}
