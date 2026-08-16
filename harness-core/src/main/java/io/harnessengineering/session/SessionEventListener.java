package io.harnessengineering.session;

/** Receives committed session events. */
@FunctionalInterface
public interface SessionEventListener {
    void onEvent(SessionEvent event);
}
