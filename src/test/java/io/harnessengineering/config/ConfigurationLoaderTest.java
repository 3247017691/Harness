package io.harnessengineering.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConfigurationLoaderTest {
    private final ConfigurationLoader loader = new ConfigurationLoader();

    @Test
    void parsesNestedGroupsAndDefaultEnabledFlags() {
        HarnessConfiguration configuration = loader.parse("""
                plugins:
                  - id: root-plugin
                    type: counter
                    options:
                      label: root
                groups:
                  - id: child
                    plugins:
                      - id: nested-plugin
                        type: counter
                        enabled: false
                    groups:
                      - id: grandchild
                        plugins: []
                """);

        assertEquals(1, configuration.plugins().size());
        assertEquals("root-plugin", configuration.plugins().getFirst().id().value());
        assertEquals("root", configuration.plugins().getFirst().options().path("label").asText());
        assertEquals(1, configuration.groups().size());
        assertEquals("child", configuration.groups().getFirst().id());
        assertFalse(configuration.groups().getFirst().plugins().getFirst().enabled());
        assertEquals("grandchild", configuration.groups().getFirst().groups().getFirst().id());
    }

    @Test
    void rejectsUnknownFieldsAndDuplicatePluginIds() {
        ConfigurationException unknown = assertThrows(ConfigurationException.class,
                () -> loader.parse("plugins: [{id: example, type: counter, unexpected: true}]"));
        assertEquals("plugins[0] contains unknown field: unexpected", unknown.getMessage());

        ConfigurationException duplicate = assertThrows(ConfigurationException.class,
                () -> loader.parse("""
                        plugins:
                          - {id: example, type: counter}
                          - {id: example, type: other}
                        """));
        assertEquals("plugins contains duplicate plugin ID: example", duplicate.getMessage());
    }
}
