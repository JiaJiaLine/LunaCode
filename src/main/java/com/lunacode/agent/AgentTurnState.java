package com.lunacode.agent;

public enum AgentTurnState {
    STARTING,
    STREAMING_MODEL,
    COLLECTING_TOOL_USE,
    EXECUTING_TOOLS,
    RECORDING_RESULTS,
    COMPLETED,
    FAILED,
    CANCELLED
}
