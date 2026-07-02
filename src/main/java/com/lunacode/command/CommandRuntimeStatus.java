package com.lunacode.command;

import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;

public record CommandRuntimeStatus(
        AgentMode agentMode,
        PermissionMode permissionMode,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String sessionShortId,
        Boolean memoryAutoUpdateEnabled,
        String memoryLatestState
) {
    public CommandRuntimeStatus {
        agentMode = agentMode == null ? AgentMode.DEFAULT : agentMode;
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
        provider = provider == null ? "" : provider;
        model = model == null ? "" : model;
        state = state == null ? "" : state;
        sessionShortId = sessionShortId == null ? "" : sessionShortId;
        memoryLatestState = memoryLatestState == null ? "" : memoryLatestState;
    }
}
