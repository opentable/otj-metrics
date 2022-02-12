package com.opentable.metrics.ready;

import static org.junit.Assert.assertEquals;

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
            void transition(boolean newState) {
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
        // Randomlu flip stuff for a random number of times, and show consistent number of transitions.
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

    }
    private ReadinessProbeEvent getEvent(boolean b) {
        return new ReadinessProbeEvent(this, b);
    }

}