package com.opentable.metrics;

import java.util.function.Function;

import javax.inject.Inject;

import com.codahale.metrics.MetricRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.opentable.serverinfo.ServerInfo;
import com.opentable.spring.ConversionServiceConfiguration;

public class GraphiteReporterPrefixTest {
    private Function<String, String> oldGetenv;

    @Inject
    private GraphiteReporter reporter;

    @Before
    public void before() {
        Assert.assertNull(oldGetenv);
        oldGetenv = GraphiteReporter.getenv;
    }

    @After
    public void after() {
        Assert.assertNotNull(oldGetenv);
        GraphiteReporter.getenv = oldGetenv;
        Assert.assertNotNull(reporter);
        reporter = null;
    }

    @Test
    public void prefixWhole() {
        final String prefix = prefixFrom("type-location.flavor", "x");
        Assert.assertEquals("app_metrics.test-server-flavor.type.location.instance-x", prefix);
    }

    @Test
    public void prefixPartial() {
        final String prefix = prefixFrom("type-location", "x");
        Assert.assertEquals("app_metrics.test-server.type.location.instance-x", prefix);
    }

    @Test
    public void real() {
        final String prefix = prefixFrom("prod-uswest2", "3");
        Assert.assertEquals("app_metrics.test-server.prod.uswest2.instance-3", prefix);
    }

    @Test
    public void unknown() {
        final String prefix = prefixFrom(null, null);
        Assert.assertEquals("app_metrics.test-server.unknown.unknown.instance-unknown", prefix);
    }

    private String prefixFrom(final String env, final String instanceNo) {
        GraphiteReporter.getenv = name -> {
            if ("OT_ENV_WHOLE".equals(name)) {
                return env;
            }
            if ("INSTANCE_NO".equals(name)) {
                return instanceNo;
            }
            return null;
        };
        ServerInfo.add(ServerInfo.SERVER_TYPE, "test-server");
        final ApplicationContext context = new AnnotationConfigApplicationContext(
                MetricRegistryConfiguration.class,
                ConversionServiceConfiguration.class,
                GraphiteReporter.class
        );
        final AutowireCapableBeanFactory factory = context.getAutowireCapableBeanFactory();
        factory.autowireBean(this);
        Assert.assertNotNull(reporter);
        final String prefix = reporter.getPrefix();
        Assert.assertNotNull(prefix);
        return prefix;
    }

    @Configuration
    public static class MetricRegistryConfiguration {
        @Bean
        public MetricRegistry getMetrics() {
            return new MetricRegistry();
        }
    }
}
