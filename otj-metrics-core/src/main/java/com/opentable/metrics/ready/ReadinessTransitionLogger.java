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
package com.opentable.metrics.ready;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.EvictingQueue;

import org.slf4j.Logger;
import org.springframework.context.event.EventListener;

import com.opentable.bucket.BucketLog;

public class ReadinessTransitionLogger {
    public static class Transition {
        private final boolean oldState;
        private final boolean newState;
        private final Instant instant;
        public Transition(boolean oldState, boolean newState) {
            this.oldState = oldState;
            this.newState = newState;
            this.instant = Instant.now();
        }

        public Instant getInstant() {
            return instant;
        }

        public boolean isNewState() {
            return newState;
        }

        public boolean isOldState() {
            return oldState;
        }

        @Override
        public String toString() {
            return instant + " " + oldState + "->" + newState;
        }
    }
    // Don't log more than 5/s. This might miss some flapping, but protects the logging framework
    private static final Logger LOG = BucketLog.perSecond(ReadinessTransitionLogger.class, 5);

    private final EvictingQueue<Transition> transitions = EvictingQueue.create(10);
    private final AtomicBoolean state = new AtomicBoolean(); // assume we are unready to begin with
    @EventListener
    // We either go from unready -> ready, ready -> unready, or don't transition
    public void onReadinessProbeEvent(ReadinessProbeEvent event)  {
        final boolean newState = event.isReady();
        // unready => ready
        if (newState) {
            if (state.compareAndSet(false, true)) {
                transition(newState);
            }
        } else {
            // ready ==> unready
            if (state.compareAndSet(true, false)) {
                transition(newState);
            }
        }
    }

    protected void transition(boolean newState) {
        LOG.debug("Transition to {} State", newState ? "READY" : "UNREADY");
        synchronized (transitions) {
            transitions.add(new Transition(!newState, newState));
        }
    }

    public List<Transition> getTransitions() {
        synchronized (transitions) {
            return new ArrayList<>(transitions);
        }
    }

    public boolean getState() {
        return state.get();
    }

    @VisibleForTesting
    void setState(boolean override) {
        state.set(override);
    }
}
