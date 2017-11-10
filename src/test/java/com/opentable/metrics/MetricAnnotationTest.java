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

import java.lang.management.ManagementFactory;
import java.util.Map;

import javax.inject.Named;
import javax.management.MBeanServer;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.opentable.service.AppInfo;
import com.opentable.service.EnvInfo;
import com.opentable.service.ServiceInfo;

public class MetricAnnotationTest {
    @Test
    public void test() {
        final ApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        final BeanFactory factory = context.getAutowireCapableBeanFactory();
        final String key = "com.opentable.metrics.MetricAnnotationTest.TestConfiguration.Annotated.timed";
        final MetricRegistry metricRegistry = factory.getBean(MetricRegistry.class);
        final Map<String, Metric> metrics = metricRegistry.getMetrics();
        Assert.assertNotNull(metrics);
        Assert.assertFalse(metrics.isEmpty());
        Assert.assertTrue(metrics.containsKey(key));
        final Timer timer = (Timer) metrics.get(key);
        Assert.assertEquals(timer.getCount(), 0);
        factory.getBean(TestConfiguration.Annotated.class).timed();
        factory.getBean(TestConfiguration.Annotated.class).timed();
        Assert.assertEquals(timer.getCount(), 2);
    }

    @Configuration
    @Import({
            AppInfo.class,
            EnvInfo.class,
            DefaultMetricsConfiguration.class,
            TestConfiguration.Annotated.class,
    })
    static class TestConfiguration {
        @Bean
        public MBeanServer getMBeanServer() {
            return ManagementFactory.getPlatformMBeanServer();
        }

        @Bean
        public ServiceInfo getServiceInfo() {
            return new ServiceInfo() {
                @Override
                public String getName() {
                    return "test-service-name";
                }
            };
        }

        @Named
        static class Annotated {
            @Timed
            void timed() {}
        }
    }
}
