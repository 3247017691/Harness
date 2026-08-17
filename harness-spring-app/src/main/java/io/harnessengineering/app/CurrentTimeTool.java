package io.harnessengineering.app;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harnessengineering.tools.Tool;
import io.harnessengineering.tools.ToolCall;
import io.harnessengineering.tools.ToolContext;
import io.harnessengineering.tools.ToolDefinition;
import io.harnessengineering.tools.ToolResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Demo tool answering with the host's current local time. */
public final class CurrentTimeTool implements Tool {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public ToolDefinition definition() {
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("type", "object");
        parameters.putObject("properties");
        parameters.putArray("required");
        return new ToolDefinition(DemoProvider.TIME_TOOL, "Returns the current local date and time, no arguments.",
                parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        return ToolResult.success(call.id(), LocalDateTime.now().format(FORMAT));
    }
}