package com.opentable.metrics;

import java.util.function.Function;

import javax.inject.Inject;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.opentable.config.FixedConfigModule;
import com.opentable.lifecycle.Lifecycle;
import com.opentable.lifecycle.LifecycleStage;
import com.opentable.lifecycle.guice.LifecycleModule;
import com.opentable.metrics.GraphiteReporter;
import com.opentable.serverinfo.ServerInfo;

public class GraphiteReporterPrefixTest {
    private Function<String, String> oldGetenv;
    @Inject
    private GraphiteReporter reporter;
    @Inject
    private Lifecycle lifecycle;

    @Before
    public void before() {
        Assert.assertNull(oldGetenv);
        oldGetenv = GraphiteReporter.getenv;
    }

    @After
    public void after() {
        Assert.assertNotNull(oldGetenv);
        GraphiteReporter.getenv = oldGetenv;
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
        final Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new GraphiteModule(),
                new FixedConfigModule(),
                new LifecycleModule());
        injector.injectMembers(this);
        Assert.assertNotNull(reporter);
        Assert.assertNotNull(lifecycle);
        lifecycle.executeTo(LifecycleStage.START_STAGE);
        final String prefix = reporter.getPrefix();
        Assert.assertNotNull(prefix);
        return prefix;
    }
}
