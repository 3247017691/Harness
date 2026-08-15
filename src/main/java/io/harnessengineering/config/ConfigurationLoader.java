package io.harnessengineering.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads and validates YAML plugin composition documents. */
public final class ConfigurationLoader {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * Loads configuration from a YAML document.
     *
     * @param path YAML file path
     * @return immutable validated configuration
     */
    public HarnessConfiguration load(Path path) {
        try {
            return parse(mapper.readTree(path.toFile()));
        } catch (IOException exception) {
            throw new ConfigurationException("cannot read configuration: " + path, exception);
        }
    }

    /**
     * Parses YAML content into immutable validated configuration.
     *
     * @param yaml YAML document
     * @return immutable validated configuration
     */
    public HarnessConfiguration parse(String yaml) {
        try {
            return parse(mapper.readTree(yaml));
        } catch (IOException exception) {
            throw new ConfigurationException("invalid YAML configuration", exception);
        }
    }

    private HarnessConfiguration parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new ConfigurationException("configuration root must be an object");
        }
        rejectUnknown(root, Set.of("plugins", "groups"), "root");
        return new HarnessConfiguration(
                parsePlugins(root.path("plugins"), "plugins"),
                parseGroups(root.path("groups"), "groups"));
    }

    private List<PluginConfiguration> parsePlugins(JsonNode node, String path) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new ConfigurationException(path + " must be an array");
        }
        List<PluginConfiguration> plugins = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            String itemPath = path + "[" + index + "]";
            if (!item.isObject()) {
                throw new ConfigurationException(itemPath + " must be an object");
            }
            rejectUnknown(item, Set.of("id", "type", "enabled", "options"), itemPath);
            String id = requiredText(item, "id", itemPath);
            if (!ids.add(id)) {
                throw new ConfigurationException(path + " contains duplicate plugin ID: " + id);
            }
            String type = requiredText(item, "type", itemPath);
            boolean enabled = optionalBoolean(item, "enabled", true, itemPath);
            JsonNode options = item.has("options") ? item.get("options") : mapper.createObjectNode();
            if (!options.isObject()) {
                throw new ConfigurationException(itemPath + ".options must be an object");
            }
            plugins.add(new PluginConfiguration(new PluginId(id), type, enabled, options));
        }
        return plugins;
    }

    private List<PluginGroupConfiguration> parseGroups(JsonNode node, String path) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new ConfigurationException(path + " must be an array");
        }
        List<PluginGroupConfiguration> groups = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            String itemPath = path + "[" + index + "]";
            if (!item.isObject()) {
                throw new ConfigurationException(itemPath + " must be an object");
            }
            rejectUnknown(item, Set.of("id", "enabled", "plugins", "groups"), itemPath);
            String id = requiredText(item, "id", itemPath);
            if (!ids.add(id)) {
                throw new ConfigurationException(path + " contains duplicate group ID: " + id);
            }
            groups.add(new PluginGroupConfiguration(
                    id,
                    optionalBoolean(item, "enabled", true, itemPath),
                    parsePlugins(item.path("plugins"), itemPath + ".plugins"),
                    parseGroups(item.path("groups"), itemPath + ".groups")));
        }
        return groups;
    }

    private static String requiredText(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new ConfigurationException(path + "." + field + " must be a non-blank string");
        }
        return value.textValue();
    }

    private static boolean optionalBoolean(JsonNode node, String field, boolean defaultValue, String path) {
        JsonNode value = node.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw new ConfigurationException(path + "." + field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed, String path) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new ConfigurationException(path + " contains unknown field: " + field);
            }
        });
    }
}
