package io.harnessengineering.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level configuration for the HarnessEngineering assembly.
 *
 * @param sessionDir directory backing the JSONL session store
 */
@ConfigurationProperties(prefix = "harness")
public record HarnessProperties(String sessionDir) {
}
