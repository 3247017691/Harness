package io.harnessengineering.core;

/**
 * Receives notifications when a service registration changes.
 */
@FunctionalInterface
public interface ServiceChangeListener {
    /**
     * Handles a service registration change.
     *
     * @param key affected service key
     * @param available true when the key is now registered
     */
    void onServiceChanged(ServiceKey<?> key, boolean available);
}
