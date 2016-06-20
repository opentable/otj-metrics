package com.opentable.metrics;

import java.util.function.Function;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.opentable.metrics.GraphiteReporter;

public class GraphiteReporterGetEnvironmentTest {
    private Function<String, String> oldGetenv;

    @Before
    public void before() {
        Assert.assertNull(oldGetenv);
        oldGetenv = GraphiteReporter.getenv;
    }

    @After
    public void after() {
        Assert.assertNotNull(oldGetenv);
        GraphiteReporter.getenv = oldGetenv;
        oldGetenv = null;
    }

    @Test
    public void whole() {
        final GraphiteReporter.Environment env = fromVar("prod-uswest2.shadow");
        Assert.assertNotNull(env);
        Assert.assertEquals("prod", env.type);
        Assert.assertEquals("uswest2", env.location);
        Assert.assertEquals("shadow", env.flavor);
    }

    @Test
    public void partial() {
        final GraphiteReporter.Environment env = fromVar("qa-sf");
        Assert.assertNotNull(env);
        Assert.assertEquals("qa", env.type);
        Assert.assertEquals("sf", env.location);
        Assert.assertNull(env.flavor);
    }

    @Test
    public void unknown() {
        final GraphiteReporter.Environment env = fromVar(null);
        Assert.assertNotNull(env);
        Assert.assertEquals("unknown", env.type);
        Assert.assertEquals("unknown", env.location);
        Assert.assertNull(env.flavor);
    }

    @Test(expected = IllegalArgumentException.class)
    public void bad() {
        fromVar("fgsfds");
    }

    private static GraphiteReporter.Environment fromVar(final String var) {
        GraphiteReporter.getenv = name -> var;
        return GraphiteReporter.getEnvironment();
    }
}
