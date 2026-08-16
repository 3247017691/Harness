package io.harnessengineering.tools;

import java.util.Objects;

/** Structured outcome of a tool invocation. */
public record ToolResult(String toolCallId, boolean success, String content, String errorCode) {
    public ToolResult {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(content, "content");
        if (toolCallId.isBlank()) {
            throw new IllegalArgumentException("tool call ID must not be blank");
        }
        if (success && errorCode != null) {
            throw new IllegalArgumentException("successful result cannot have an error code");
        }
        if (!success && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("failed result requires an error code");
        }
    }

    public static ToolResult success(String callId, String content) {
        return new ToolResult(callId, true, content, null);
    }

    public static ToolResult failure(String callId, String errorCode, String content) {
        return new ToolResult(callId, false, content, errorCode);
    }
}
