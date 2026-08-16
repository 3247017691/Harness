package io.harnessengineering.agent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation signal owned by one Agent turn. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("agent operation cancelled");
        }
    }
}
