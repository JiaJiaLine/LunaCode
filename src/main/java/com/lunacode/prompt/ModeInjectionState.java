package com.lunacode.prompt;

import com.lunacode.runtime.AgentMode;

import java.nio.file.Path;
import java.util.Objects;

public record ModeInjectionState(AgentMode mode, int turnIndex, Path planFile, int repeatInterval) {
    public ModeInjectionState {
        mode = mode == null ? AgentMode.DEFAULT : mode;
        if (turnIndex <= 0) {
            throw new IllegalArgumentException("turnIndex 必须大于 0");
        }
        planFile = Objects.requireNonNull(planFile, "planFile");
        repeatInterval = repeatInterval <= 0 ? 3 : repeatInterval;
    }
}
