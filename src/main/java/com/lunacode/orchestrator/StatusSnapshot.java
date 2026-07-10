package com.lunacode.orchestrator;

import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;

import java.util.List;

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
        String memoryLatestState,
        List<BackgroundActivitySnapshot> backgroundActivities
) {
    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, null, null, AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null, List.of());
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null, List.of());
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary, PermissionMode permissionMode) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, AgentMode.DEFAULT, permissionMode, null, null, null, List.of());
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary, AgentMode agentMode, PermissionMode permissionMode) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, permissionMode, null, null, null, List.of());
    }

    public StatusSnapshot(
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
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, permissionMode,
                sessionShortId, memoryAutoUpdateEnabled, memoryLatestState, List.of());
    }

    public StatusSnapshot {
        agentMode = agentMode == null ? AgentMode.DEFAULT : agentMode;
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
        backgroundActivities = backgroundActivities == null ? List.of() : List.copyOf(backgroundActivities);
    }

    public StatusSnapshot withAgentMode(AgentMode mode) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, mode, permissionMode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState, backgroundActivities);
    }

    public StatusSnapshot withPermissionMode(PermissionMode mode) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, mode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState, backgroundActivities);
    }

    public StatusSnapshot withSessionAndMemory(String sessionShortId, Boolean memoryAutoUpdateEnabled, String memoryLatestState) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, permissionMode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState, backgroundActivities);
    }

    public StatusSnapshot withBackgroundActivities(List<BackgroundActivitySnapshot> activities) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, agentMode, permissionMode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState, activities);
    }

    public static StatusSnapshot idle(String provider, String model) {
        return new StatusSnapshot(provider, model, null, null, "idle", null, null, null, AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null, List.of());
    }
}
