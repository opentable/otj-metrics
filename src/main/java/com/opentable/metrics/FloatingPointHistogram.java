package com.opentable.metrics;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;

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
