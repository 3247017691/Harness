package io.harnessengineering.tools;

/** Executable capability that can be advertised to an LLM provider. */
public interface Tool {
    ToolDefinition definition();

    ToolResult execute(ToolCall call, ToolContext context) throws Exception;
}
