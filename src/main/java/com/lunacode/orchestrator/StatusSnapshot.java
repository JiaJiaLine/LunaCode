package com.lunacode.orchestrator;

import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;

public record StatusSnapshot(
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String errorSummary,
        String toolName,
        String toolSummary,
        AgentMode agentMode,
        PermissionMode permissionMode,
        String sessionShortId,
        Boolean memoryAutoUpdateEnabled,
        String memoryLatestState
) {
    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, null, null, AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null);
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null);
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary, PermissionMode permissionMode) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, AgentMode.DEFAULT, permissionMode, null, null, null);
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary, AgentMode agentMode, PermissionMode permissionMode) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, permissionMode, null, null, null);
    }

    public StatusSnapshot {
        agentMode = agentMode == null ? AgentMode.DEFAULT : agentMode;
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
    }

    public StatusSnapshot withAgentMode(AgentMode mode) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, mode, permissionMode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState);
    }

    public StatusSnapshot withPermissionMode(PermissionMode mode) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, mode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState);
    }

    public StatusSnapshot withSessionAndMemory(String sessionShortId, Boolean memoryAutoUpdateEnabled, String memoryLatestState) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, permissionMode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState);
    }

    public static StatusSnapshot idle(String provider, String model) {
        return new StatusSnapshot(provider, model, null, null, "idle", null, null, null, AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null);
    }
}