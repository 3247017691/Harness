package io.harnessengineering.tools;

import java.time.Duration;
import java.util.Objects;

/** Retry schedule for failed tool executions. */
public record RetryPolicy(int maxAttempts, Duration delay) {
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
    }

    /** @return a policy that never retries */
    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO);
    }

    /** @return a policy retrying up to {@code maxAttempts} times with a fixed delay between attempts */
    public static RetryPolicy of(int maxAttempts, Duration delay) {
        return new RetryPolicy(maxAttempts, delay);
    }
}
