package io.harnessengineering.agent;

/** Observable lifecycle state for an agent loop. */
public enum AgentState {
    NEW,
    IDLE,
    RUNNING,
    CLOSED
}
