package com.opentable.metrics.ready;

@FunctionalInterface
public interface ReadyCheckFilter {
    /**
     * Matches all ready checks, regardless of type or name.
     */
    ReadyCheckFilter ALL = (name, readyCheck) -> true;

    /**
     * Returns {@code true} if the ready check matches the filter; {@code false} otherwise.
     *
     * @param name        the ready check's name
     * @param readyCheck the ready check
     * @return {@code true} if the ready check matches the filter
     */
    boolean matches(String name, ReadyCheck readyCheck);
}