package com.opentable.metrics;

import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Gauge;

/** A fine cross-breed of an {@link AtomicLong} and a {@link Gauge<Long>}. */
public class AtomicLongGauge extends AtomicLong implements Gauge<Long> {
    private static final long serialVersionUID = 1L;
    @Override
    public Long getValue() {
        return get();
    }
}
