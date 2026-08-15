package io.harnessengineering.session;

/** Event type names understood by the built-in message projection. */
public final class SessionEventTypes {
    public static final String USER_MESSAGE = "user/message";
    public static final String ASSISTANT_MESSAGE = "assistant/message";
    public static final String TOOL_RESULT = "tool/result";

    private SessionEventTypes() { }
}
