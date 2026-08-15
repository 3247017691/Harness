package io.harnessengineering.config;

import io.harnessengineering.core.Plugin;

/** Creates plugins from validated declarative configuration. */
@FunctionalInterface
public interface PluginFactory {
    /**
     * Creates a plugin instance.
     *
     * @param configuration validated configuration for one enabled plugin
     * @return plugin instance
     * @throws Exception when configuration cannot produce a plugin
     */
    Plugin create(PluginConfiguration configuration) throws Exception;
}
