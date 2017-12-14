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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;

/**
 * This class extends {@link Histogram} to include floating-point {@link #update} functions. Because the underlying
 * {@link Reservoir}s store only {@code long}s, this class also takes a {@link #scale} by which floating-point
 * values are <em>multiplied</em> before being rounded and stored as {@code long}s. The {@link Snapshot} returned
 * wraps the underlying {@link Histogram#getSnapshot()} return value with an implementation that
 * &ldquo;un-scales&rdquo; the returned values by <em>dividing</em> by the {@link #scale}.
 *
 * <p>
 * This is essentially just the ol' &ldquo;convert to cents&rdquo; trick, but you may choose the magnitude of the
 * {@link #scale} based on the expected range of floating-point values stored as well as the desired precision you
 * wish to preserve.
 *
 * <p>
 * You may still of course still call {@link #update(int)} or {@link #update(long)}; behavior in this case is
 * undefined.
 *
 * <p>
 * cf. <a href="https://github.com/dropwizard/metrics/issues/863">this issue</a>
 */
public class FloatingPointHistogram extends Histogram {
    private final double scale;

    public FloatingPointHistogram(final Reservoir reservoir, final double scale) {
        super(reservoir);
        this.scale = scale;
    }

    public FloatingPointHistogram(final Reservoir reservoir) {
        this(reservoir, 1);
    }

    public void update(final float value) {
        update((double) value);
    }

    public void update(final double value) {
        update(Math.round(scale * value));
    }

    @Override
    public Snapshot getSnapshot() {
        return new ScaledSnapshot(super.getSnapshot());
    }

    private class ScaledSnapshot extends Snapshot {
        private final Snapshot snap;

        private ScaledSnapshot(final Snapshot snap) {
            this.snap = snap;
        }

        private double scaled(final double value) {
            return value / scale;
        }

        private long scaled(final long value) {
            return Math.round(scaled((double) value));
        }

        @Override
        public double getValue(final double quantile) {
            return scaled(snap.getValue(quantile));
        }

        @Override
        public long[] getValues() {
            return Arrays
                    .stream(snap.getValues())
                    .map(this::scaled)
                    .toArray();
        }

        @Override
        public int size() {
            return snap.size();
        }

        @Override
        public long getMax() {
            return scaled(snap.getMax());
        }

        @Override
        public double getMean() {
            return scaled(snap.getMean());
        }

        @Override
        public long getMin() {
            return scaled(snap.getMin());
        }

        @Override
        public double getStdDev() {
            return scaled(snap.getStdDev());
        }

        @Override
        public void dump(final OutputStream output) {
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                for (long value : getValues()) {
                    out.printf("%d%n", value);
                }
            }
        }
    }
}
