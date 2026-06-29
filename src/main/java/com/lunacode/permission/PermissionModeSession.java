package com.lunacode.permission;

import com.lunacode.runtime.AgentMode;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class PermissionModeSession {
    private final AtomicReference<PermissionMode> currentMode;

    public PermissionModeSession(PermissionMode initialMode) {
        this.currentMode = new AtomicReference<>(initialMode == null ? PermissionMode.DEFAULT : initialMode);
    }

    public PermissionMode currentMode() {
        return currentMode.get();
    }

    public void setCurrentMode(PermissionMode mode) {
        currentMode.set(Objects.requireNonNull(mode, "mode"));
    }

    public PermissionMode modeFor(AgentMode agentMode) {
        return agentMode == AgentMode.PLAN ? PermissionMode.PLAN : currentMode();
    }
}
