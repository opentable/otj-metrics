package com.opentable.metrics.http;

import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.AdminServlet;
import com.codahale.metrics.servlets.MetricsServlet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        MetricsHttpResource.class,
        MetricsHttpConfiguration.Servlet.class,
})
public class MetricsHttpConfiguration {
    @Bean
    public ServletContextListener getMetricsContextListener(final MetricRegistry metrics) {
        return new MetricsContextListener(metrics);
    }

    @Named
    static class Servlet {
        @Value("${ot.metrics.http.path:/metrics}")
        private String path;

        private final ServletContext container;

        Servlet(final ServletContext container) {
            this.container = container;
        }

        @PostConstruct
        public void postConstruct() {
            final String urlPattern = path + "*";
            container.addServlet(AdminServlet.class.getCanonicalName(), AdminServlet.class).addMapping(urlPattern);
        }
    }

    private static class MetricsContextListener extends MetricsServlet.ContextListener {
        private final MetricRegistry metrics;

        private MetricsContextListener(MetricRegistry metrics)
        {
            this.metrics = metrics;
        }

        @Override
        protected MetricRegistry getMetricRegistry()
        {
            return metrics;
        }
    }
}
