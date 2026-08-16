package io.harnessengineering.config;

import java.util.List;
import java.util.Objects;

/** A named isolated scope containing configured plugins and child groups. */
public record PluginGroupConfiguration(
        String id,
        boolean enabled,
        List<PluginConfiguration> plugins,
        List<PluginGroupConfiguration> groups) {
    public PluginGroupConfiguration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(plugins, "plugins");
        Objects.requireNonNull(groups, "groups");
        if (id.isBlank()) {
            throw new IllegalArgumentException("group ID must not be blank");
        }
        plugins = List.copyOf(plugins);
        groups = List.copyOf(groups);
    }
}
