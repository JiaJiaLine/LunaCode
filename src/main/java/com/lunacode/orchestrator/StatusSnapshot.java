package com.lunacode.orchestrator;

import com.lunacode.permission.PermissionMode;

public record StatusSnapshot(
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String errorSummary,
        String toolName,
        String toolSummary,
        PermissionMode permissionMode,
        String sessionShortId,
        Boolean memoryAutoUpdateEnabled,
        String memoryLatestState
) {
    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, null, null, PermissionMode.DEFAULT, null, null, null);
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, PermissionMode.DEFAULT, null, null, null);
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary, PermissionMode permissionMode) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, permissionMode, null, null, null);
    }

    public StatusSnapshot {
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
    }

    public StatusSnapshot withPermissionMode(PermissionMode mode) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, mode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState);
    }

    public StatusSnapshot withSessionAndMemory(String sessionShortId, Boolean memoryAutoUpdateEnabled, String memoryLatestState) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, permissionMode, sessionShortId, memoryAutoUpdateEnabled, memoryLatestState);
    }

    public static StatusSnapshot idle(String provider, String model) {
        return new StatusSnapshot(provider, model, null, null, "idle", null, null, null, PermissionMode.DEFAULT, null, null, null);
    }
}
