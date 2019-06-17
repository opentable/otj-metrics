package com.opentable.metrics.ready;

import java.util.EventListener;

/**
 * A listener contract for {@link ReadyCheckRegistry} events.
 */
public interface ReadyCheckRegistryListener {

    /**
     * Called when a new {@link ReadyCheck} is added to the registry.
     *
     * @param name        the name of the ready check
     * @param readyCheck the ready check
     */
    void onReadyCheckAdded(String name, ReadyCheck readyCheck);

    /**
     * Called when a {@link ReadyCheck} is removed from the registry.
     *
     * @param name        the name of the ready check
     * @param readyCheck the ready check
     */
    void onReadyCheckRemoved(String name, ReadyCheck readyCheck);

}
