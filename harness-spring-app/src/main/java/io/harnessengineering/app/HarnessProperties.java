package io.harnessengineering.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level configuration for the HarnessEngineering assembly.
 *
 * @param sessionDir directory backing the JSONL session store
 * @param httpPort port for the read-only HTTP/SSE server
 */
@ConfigurationProperties(prefix = "harness")
public record HarnessProperties(String sessionDir, int httpPort) {
}
