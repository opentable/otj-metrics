package com.opentable.metrics.graphite;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteReporter;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.ServiceInfo;

public class GraphiteReporterPrefixTest {
    @Inject
    private GraphiteReporter reporter;

    @Test
    public void withFlavor() {
        final String prefix = prefixFrom("type-location.flavor", "0");
        Assert.assertNotNull(prefix);
        Assert.assertEquals("app_metrics.test-server-flavor.type.location.instance-0", prefix);
    }

    @Test
    public void noFlavor() {
        final String prefix = prefixFrom("prod-uswest2", "3");
        Assert.assertNotNull(prefix);
        Assert.assertEquals("app_metrics.test-server.prod.uswest2.instance-3", prefix);
    }

    @Test
    public void bad() {
        Assert.assertNull(prefixFrom(null, null));
    }

    private String prefixFrom(final String env, final String instanceNo) {
        final SpringApplication app = new SpringApplication(
                TestConfiguration.class,
                GraphiteConfiguration.class
        );
        final Map<String, Object> mockEnv = new HashMap<>();
        if (env != null) {
            mockEnv.put("OT_ENV_WHOLE", env);
            final String[] typeLoc = env.split("-");
            mockEnv.put("OT_ENV_TYPE", typeLoc[0]);
            final String[] locFlavor = typeLoc[1].split("\\.");
            mockEnv.put("OT_ENV_LOCATION", locFlavor[0]);
            mockEnv.put("OT_ENV_FLAVOR", locFlavor.length == 2 ? locFlavor[1] : "");
            mockEnv.put("OT_ENV", typeLoc[0] + "-" + locFlavor[0]);
            mockEnv.put("ot.graphite.graphite-host", "carbon-foo-bar-baz.otenv.com");
        }
        if (instanceNo != null) {
            mockEnv.put("INSTANCE_NO", instanceNo);
        }
        app.setDefaultProperties(mockEnv);
        app.run().getAutowireCapableBeanFactory().autowireBean(this);
        if (reporter == null) {
            return null;
        }
        try {
            Field f = reporter.getClass().getDeclaredField("prefix");
            f.setAccessible(true);
            return Objects.toString(f.get(reporter));
        } catch (IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new AssertionError(e);
        }
    }

    @Configuration
    @Import({
            AppInfo.class,
            EnvInfo.class,
    })
    public static class TestConfiguration {
        @Bean
        public ServiceInfo getServiceInfo() {
            return new ServiceInfo() {
                @Override
                public String getName() {
                    return "test-server";
                }
            };
        }
        @Bean
        public MetricRegistry getMetrics() {
            return new MetricRegistry();
        }
    }
}
