package io.harnessengineering.tools;

/** Middleware that may inspect or replace a tool execution result. */
@FunctionalInterface
public interface ToolMiddleware {
    ToolResult execute(ToolCall call, ToolContext context, Next next) throws Exception;

    @FunctionalInterface
    interface Next {
        ToolResult execute(ToolCall call, ToolContext context) throws Exception;
    }
}
