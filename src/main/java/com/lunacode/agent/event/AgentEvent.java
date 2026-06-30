package com.lunacode.agent.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.permission.PermissionMode;
import com.lunacode.tool.ToolResult;

import java.time.Duration;

public sealed interface AgentEvent permits
        AgentEvent.StreamText,
        AgentEvent.ToolUseStarted,
        AgentEvent.PermissionRequested,
        AgentEvent.PermissionAllowed,
        AgentEvent.PermissionDenied,
        AgentEvent.PermissionModeChanged,
        AgentEvent.PermissionRuleWarning,
        AgentEvent.ToolResultReady,
        AgentEvent.TurnComplete,
        AgentEvent.LoopComplete,
        AgentEvent.UsageUpdated,
        AgentEvent.ErrorOccurred {

    record StreamText(String text) implements AgentEvent {}

    record ToolUseStarted(String requestId, String toolName, JsonNode input) implements AgentEvent {}

    record PermissionRequested(String requestId, String toolName, String prompt) implements AgentEvent {}

    record PermissionAllowed(String requestId, String toolName, String reason) implements AgentEvent {}

    record PermissionDenied(String requestId, String toolName, String reason) implements AgentEvent {}

    record PermissionModeChanged(PermissionMode mode) implements AgentEvent {}

    record PermissionRuleWarning(String message) implements AgentEvent {}

    record ToolResultReady(String requestId, String toolName, ToolResult result, Duration duration) implements AgentEvent {}

    record TurnComplete(int turnIndex) implements AgentEvent {}

    record LoopComplete(int totalTurns) implements AgentEvent {}

    record UsageUpdated(TokenUsage cumulativeUsage) implements AgentEvent {}

    record ErrorOccurred(String message, Throwable cause) implements AgentEvent {}
}
