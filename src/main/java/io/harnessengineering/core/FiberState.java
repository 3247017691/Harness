package io.harnessengineering.core;

/** Lifecycle states for a mounted plugin. */
public enum FiberState {
    PENDING,
    LOADING,
    ACTIVE,
    FAILED,
    UNLOADING,
    DISPOSED
}
