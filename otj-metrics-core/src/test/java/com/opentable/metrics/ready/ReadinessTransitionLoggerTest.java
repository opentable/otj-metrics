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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;


public class ReadinessTransitionLoggerTest {
    private ReadinessTransitionLogger readinessTransitionLogger;
    private final AtomicInteger counter = new AtomicInteger();
    @Before
    public void before() {
        readinessTransitionLogger = new ReadinessTransitionLogger() {
            @Override
            public void transition(boolean newState) {
                counter.incrementAndGet();
               super.transition(newState);
            }
        };
        counter.set(0);
    }

    private void genericTest(boolean oldState, boolean newState) {
        readinessTransitionLogger.setState(oldState);
        final int previousCounter = counter.get();
        readinessTransitionLogger.onReadinessProbeEvent(getEvent(newState));
        assertEquals(newState, readinessTransitionLogger.getState());
        if (oldState != newState) {
            assertEquals(previousCounter + 1, counter.get()); // transitioned
        } else {
            assertEquals(previousCounter, counter.get());
        }
    }
    @Test
    public void unreadyToReady() {
        genericTest(false, true);
    }

    @Test
    public void readyToUnready() {
        genericTest(true, false);
    }

    @Test
    public void readyToReady() {
        genericTest(true, true);
    }

    @Test
    public void unreadyToUnready() {
        genericTest(false, false);
    }

    @Test
    public void assertStateMachine() {
        final AtomicInteger independentCounter = new AtomicInteger(counter.get());
        final int upper = ThreadLocalRandom.current().nextInt(10, 50);
        // Randomly flip stuff for a random number of times, and show consistent number of transitions.
        IntStream.range(0, upper)
                .forEachOrdered(t -> {
                    boolean oldState = readinessTransitionLogger.getState();
                    boolean newState = ThreadLocalRandom.current().nextBoolean();
                    readinessTransitionLogger.onReadinessProbeEvent(getEvent(newState));
                    if (oldState != newState) {
                        independentCounter.incrementAndGet();
                    }
                });
        assertEquals(independentCounter.get(), counter.get());

        List<ReadinessTransitionLogger.Transition> transitionList = readinessTransitionLogger.getTransitions();
        // Given this test is single threaded (if multithreaded things could get interleaved),
        // we expect the parity to sum to 0
        Boolean previousNewState = null;
        Long previousInstance = null;
        for (ReadinessTransitionLogger.Transition t : transitionList) {
           // old state should equal previous steps new state, newState should= !oldState
            assertTrue(t.isNewState() != t.isOldState());
            if (previousNewState != null) {
                assertEquals(t.isOldState(), previousNewState);
            }
            // monotonically increases.
            if (previousInstance != null) {
                assertTrue(t.getInstant().toEpochMilli() >= previousInstance);
            }
            previousNewState = t.isNewState();
            previousInstance = t.getInstant().toEpochMilli();

        }
    }
    private ReadinessProbeEvent getEvent(boolean b) {
        return new ReadinessProbeEvent(this, b);
    }

}