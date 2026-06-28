package com.lunacode.agent.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.tool.ToolResult;

import java.time.Duration;

public sealed interface AgentEvent permits
        AgentEvent.StreamText,
        AgentEvent.ToolUseStarted,
        AgentEvent.ToolResultReady,
        AgentEvent.TurnComplete,
        AgentEvent.LoopComplete,
        AgentEvent.UsageUpdated,
        AgentEvent.ErrorOccurred {

    record StreamText(String text) implements AgentEvent {}

    record ToolUseStarted(String requestId, String toolName, JsonNode input) implements AgentEvent {}

    record ToolResultReady(String requestId, String toolName, ToolResult result, Duration duration) implements AgentEvent {}

    record TurnComplete(int turnIndex) implements AgentEvent {}

    record LoopComplete(int totalTurns) implements AgentEvent {}

    record UsageUpdated(TokenUsage cumulativeUsage) implements AgentEvent {}

    record ErrorOccurred(String message, Throwable cause) implements AgentEvent {}
}
