package com.opentable.metrics.ready;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;


public class ReadinessTransitionLoggerTest {
    private ReadinessTransitionLogger readinessTransitionLogger;
    private AtomicInteger counter = new AtomicInteger();
    @Before
    public void before() {

        readinessTransitionLogger = new ReadinessTransitionLogger() {
            @Override
            void transition(boolean newState) {
               counter.incrementAndGet();
               super.transition(newState);
            }
        };
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
        readyToUnready();
        readyToReady();
        readyToReady();
        readyToUnready();
        unreadyToUnready();
    }
    private ReadinessProbeEvent getEvent(boolean b) {
        return new ReadinessProbeEvent(new Object(), b);
    }

}