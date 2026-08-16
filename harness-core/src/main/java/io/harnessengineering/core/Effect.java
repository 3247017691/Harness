package io.harnessengineering.core;

/**
 * A reversible side effect owned by a plugin fiber.
 */
@FunctionalInterface
public interface Effect extends AutoCloseable {
    /**
     * Reverses this effect. Implementations must be idempotent.
     */
    @Override
    void close();

    /**
     * An effect that performs no cleanup.
     *
     * @return a no-op effect
     */
    static Effect noop() {
        return () -> { };
    }
}
