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
package com.opentable.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.util.Map;

import javax.management.MBeanServer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;

import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;

import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.ServiceInfo;

public class MetricSetBuilderTest {
    private static final String[] EXPECTED = new String[] { "bar.timer", "bar.enum.FOO", "bar.enum.BAR" };

    @Test
    public void testTrivial() {
        final MetricSetBuilder b = new MetricSetBuilder();
        assertThat(b.build().getMetrics()).isEmpty();
    }

    @Test
    public void testEmpty() {
        assertThat(
                new MetricSetBuilder(new MetricRegistry()).build().getMetrics())
            .isEmpty();
    }

    @Test
    public void testMeterRegisters() {
        final MetricRegistry testRegistry = new MetricRegistry();
        final MetricSetBuilder b = new MetricSetBuilder(testRegistry);
        b.setPrefix("test");
        final Meter foo = b.meter("foo");

        assertThat(testRegistry.getMetrics()).isEmpty();
        final Map<String, Metric> builtMetrics = b.build().getMetrics();
        assertThat(builtMetrics).containsEntry("test.foo", foo);
        assertThat(testRegistry.getMetrics()).isEqualTo(builtMetrics);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPrefixWithDot() {
        new MetricSetBuilder().setPrefix("foo.");
    }

    @Test
    public void testContextRegister() {
        try (ConfigurableApplicationContext ctx = new AnnotationConfigApplicationContext(ContextRegister.class)) {
            assertThat(ctx.getBean(MetricRegistry.class).getMetrics()).containsKeys(EXPECTED);
        }
    }

    @Configuration
    @Import({
        TestBase.class,
        DefaultMetricsConfiguration.class
    })
    static class ContextRegister {
        Timer timer;
        Map<SomeEnum, AtomicLongGauge> enumGauges;

        @Bean
        public MetricSet someMetrics(MetricSetBuilder b) {
            b.setPrefix("bar");
            timer = b.timer("timer");
            enumGauges = b.enumMetrics("enum", SomeEnum.class, AtomicLongGauge::new);
            return b.build();
        }
    }


    @Test
    public void testEventRegister() {
        final MetricRegistry registry;
        try (ConfigurableApplicationContext ctx = new AnnotationConfigApplicationContext(EventRegister.class)) {
            registry = ctx.getBean(MetricRegistry.class);
            assertThat(registry.getMetrics())
                .doesNotContainKeys(EXPECTED);
            ctx.getBean(ApplicationEventMulticaster.class).multicastEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], ctx));
            assertThat(registry.getMetrics())
                .containsKeys(EXPECTED);
        }
    }

    @Configuration
    @Import({
        TestBase.class,
        DefaultMetricsConfiguration.class
    })
    static class EventRegister {
        Timer timer;
        Map<SomeEnum, AtomicLongGauge> enumGauges;

        @Bean
        public MetricSet someMetrics(MetricSetBuilder b) {
            b.registerOnEvent(ApplicationReadyEvent.class);
            b.setPrefix("bar");
            timer = b.timer("timer");
            enumGauges = b.enumMetrics("enum", SomeEnum.class, AtomicLongGauge::new);
            return b.build();
        }
    }

    enum SomeEnum {
        FOO, BAR
    }

    @Configuration
    @Import({AppInfo.class, EnvInfo.class})
    static class TestBase {
        @Bean
        public MBeanServer mbeanServer() {
            return ManagementFactory.getPlatformMBeanServer();
        }

        @Bean
        public ServiceInfo serviceInfo() {
            return new ServiceInfo() {
                @Override
                public String getName() {
                    return "test";
                }
            };
        }
    }
}
