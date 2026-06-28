package com.lunacode.agent.turn;

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
