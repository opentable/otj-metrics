package com.opentable.metrics.ready;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class ReadinessTransitionLoggerTest {
    ReadinessTransitionLogger readinessTransitionLogger;

    @Before
    public void before() {
        readinessTransitionLogger = new ReadinessTransitionLogger();
    }

    @Test
    public void unreadyToReady() {
        readinessTransitionLogger.setState(false);
        readinessTransitionLogger.onReadinessProbeEvent(getEvent(true));
        assertTrue(readinessTransitionLogger.getState());
    }

    @Test
    public void readyToUnready() {
        readinessTransitionLogger.setState(true);
        readinessTransitionLogger.onReadinessProbeEvent(getEvent(false));
        assertFalse(readinessTransitionLogger.getState());
    }

    @Test
    public void readyToReady() {
        readinessTransitionLogger.setState(true);
        readinessTransitionLogger.onReadinessProbeEvent(getEvent(true));
        assertTrue(readinessTransitionLogger.getState());
    }

    @Test
    public void unreadyToUnready() {
        readinessTransitionLogger.setState(false);
        readinessTransitionLogger.onReadinessProbeEvent(getEvent(false));
        assertFalse(readinessTransitionLogger.getState());
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