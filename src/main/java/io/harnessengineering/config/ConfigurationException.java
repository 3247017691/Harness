package io.harnessengineering.config;

/** Reports invalid composition configuration with a precise configuration path. */
public final class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
