package io.harnessengineering.tools;

import io.harnessengineering.core.Effect;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry whose tool contributions disappear when their registration effect closes. */
public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public synchronized Effect register(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        String name = tool.definition().name();
        if (tools.containsKey(name)) {
            throw new IllegalStateException("tool already registered: " + name);
        }
        tools.put(name, tool);
        return new Registration(name, tool);
    }

    public synchronized Tool find(String name) {
        return tools.get(name);
    }

    public synchronized List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    private synchronized void remove(String name, Tool expected) {
        if (tools.get(name) == expected) {
            tools.remove(name);
        }
    }

    private final class Registration implements Effect {
        private final String name;
        private final Tool tool;
        private boolean closed;

        private Registration(String name, Tool tool) {
            this.name = name;
            this.tool = tool;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                remove(name, tool);
            }
        }
    }
}
