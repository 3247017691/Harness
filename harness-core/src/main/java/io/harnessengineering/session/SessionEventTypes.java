package io.harnessengineering.session;

/** Event type names understood by the built-in message projection. */
public final class SessionEventTypes {
    public static final String USER_MESSAGE = "user/message";
    public static final String ASSISTANT_MESSAGE = "assistant/message";
    public static final String TURN_START = "turn/start";
    public static final String TURN_END = "turn/end";
    public static final String STEP_START = "step/start";
    public static final String STEP_END = "step/end";
    public static final String ASSISTANT_CHUNK = "assistant/chunk";
    public static final String TOOL_CALL = "tool/call";
    public static final String TOOL_RESULT = "tool/result";

    private SessionEventTypes() { }
}
