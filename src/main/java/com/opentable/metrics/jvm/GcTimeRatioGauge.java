package com.opentable.metrics.jvm;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.codahale.metrics.RatioGauge;

class GcTimeRatioGauge extends RatioGauge {
    private final AtomicReference<Ratio> ratio = new AtomicReference<>(Ratio.of(0, 0));

    /**
     * @param totalTime the total time spent in GC
     * @param endTime the duration from JVM startup to the end of the latest GC run
     */
    void set(final Duration totalTime, final Duration endTime) {
        ratio.set(Ratio.of(totalTime.toMillis(), endTime.toMillis()));
    }

    @Override
    protected Ratio getRatio() {
        return ratio.get();
    }
}
