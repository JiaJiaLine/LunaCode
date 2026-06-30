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
        PermissionMode permissionMode
) {
    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, null, null, PermissionMode.DEFAULT);
    }

    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary, String toolName, String toolSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, PermissionMode.DEFAULT);
    }

    public StatusSnapshot {
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
    }

    public StatusSnapshot withPermissionMode(PermissionMode mode) {
        return new StatusSnapshot(provider, model, inputTokens, outputTokens, state, errorSummary, toolName, toolSummary, mode);
    }

    public static StatusSnapshot idle(String provider, String model) {
        return new StatusSnapshot(provider, model, null, null, "idle", null, null, null, PermissionMode.DEFAULT);
    }
}
