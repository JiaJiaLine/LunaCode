package com.lunacode.hook;

import java.nio.file.Path;
import java.util.Objects;

public record HookExecutionScope(String sessionId, int turnIndex, Path workspaceRoot) {
    public HookExecutionScope {
        sessionId = sessionId == null || sessionId.isBlank() ? "unknown-session" : sessionId;
        turnIndex = Math.max(0, turnIndex);
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
    }
}
