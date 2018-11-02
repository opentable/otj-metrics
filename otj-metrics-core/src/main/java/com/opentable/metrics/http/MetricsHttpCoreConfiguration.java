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
