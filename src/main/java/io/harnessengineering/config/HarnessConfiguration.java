package io.harnessengineering.config;

import java.util.List;
import java.util.Objects;

/** Root configuration document for the plugin composition runtime. */
public record HarnessConfiguration(List<PluginConfiguration> plugins, List<PluginGroupConfiguration> groups) {
    public HarnessConfiguration {
        Objects.requireNonNull(plugins, "plugins");
        Objects.requireNonNull(groups, "groups");
        plugins = List.copyOf(plugins);
        groups = List.copyOf(groups);
    }
}
