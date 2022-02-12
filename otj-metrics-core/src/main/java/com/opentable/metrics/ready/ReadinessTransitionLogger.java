package com.opentable.metrics.ready;

import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.springframework.context.event.EventListener;

import com.opentable.bucket.BucketLog;

public class ReadinessTransitionLogger {

    // Don't log more than 5/s. This might miss some flapping, but protects the logging framework
    private static final Logger LOG = BucketLog.perSecond(ReadinessTransitionLogger.class, 5);

    private final AtomicBoolean state = new AtomicBoolean(); // assume we are unready to begin with
    @EventListener
    // We either go from unready -> ready, ready -> unready, or don't transition
    public void onReadinessProbeEvent(ReadinessProbeEvent event)  {
        final boolean newState = event.isReady();
        // unready => ready
        if (state.compareAndSet(false, newState)) {
            LOG.debug("Transition to READY state");
        } else {
            // ready ==> unready
            if (state.compareAndSet(true, event.isReady())) {
                LOG.debug("Transition to UNREADY state");
            }
        }
    }

    @VisibleForTesting
    boolean getState() {
        return state.get();
    }

    @VisibleForTesting
    void setState(boolean override) {
        state.set(override);
    }
}
