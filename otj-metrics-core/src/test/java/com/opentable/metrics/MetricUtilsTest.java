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

import java.util.Collections;
import java.util.Map;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.google.common.collect.ImmutableMap;

import org.junit.Assert;
import org.junit.Test;

public class MetricUtilsTest {
    @Test(expected = IllegalArgumentException.class)
    public void extractLongUnsupported() {
        MetricUtils.extractLong(new Metric() {});
    }

    @Test
    public void extractCounter() {
        final Counter c = new Counter();
        final long x = 1234;
        c.inc(x);
        Assert.assertEquals(x, MetricUtils.extractLong(c));
    }

    @Test
    public void extractGaugeInt() {
        final int x = 1234;
        final Gauge<Integer> g = () -> x;
        Assert.assertEquals(x, MetricUtils.extractLong(g));
    }

    @Test
    public void extractGaugeLong() {
        final long x = 123123;
        final Gauge<Long> g = () -> x;
        Assert.assertEquals(x, MetricUtils.extractLong(g));
    }

    @Test
    public void extractFromMap() {
        final long x = 0xdeadbeef;
        final Map<String, Metric> map = Collections.singletonMap("foo.bar", (Gauge<Long>) () -> x);
        Assert.assertEquals(x, MetricUtils.extractLong(() -> map, "foo.bar"));
    }

    @Test
    public void toStringSimple() {
        final Counter c = new Counter();
        final long x = 1827497;
        c.inc(x);
        Assert.assertEquals(Long.toString(x), MetricUtils.toString(c));
    }

    @Test(expected = IllegalArgumentException.class)
    public void toStringFail() {
        MetricUtils.toString(new Metric() {});
    }

    @Test
    public void toStringMap() {
        final Meter m = new Meter();
        m.mark();
        m.mark();
        m.mark();
        final Map<String, Metric> map = new ImmutableMap.Builder<String, Metric>()
                .put("bar.baz", m)
                .put("foo", m)
                .build();
        Assert.assertEquals("{bar.baz=3, foo=3}", MetricUtils.toString(() -> map));
    }

    @Test
    public void assertMetricsEqual() {
        final Counter c = new Counter();
        final long x = 1029380983;
        c.inc(x);
        final Map<String, Metric> map = Collections.singletonMap("foo.bar", c);
        MetricUtils.assertMetricsEqual(() -> map, Collections.singletonMap("foo.bar", x));
    }

    @Test(expected = AssertionError.class)
    public void assertMetricsEqualFail() {
        final Meter m = new Meter();
        final long x = 123;
        m.mark(x);
        final Map<String, Metric> map = Collections.singletonMap("bar.baz", m);
        MetricUtils.assertMetricsEqual(() -> map, Collections.singletonMap("bar.baz", x + 10));
    }
}
