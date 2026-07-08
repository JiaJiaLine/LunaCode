package com.lunacode.coordinator;

import java.util.Set;

public record CoordinatorModeState(
        boolean enabled,
        Set<String> allowedTools,
        String systemPrompt
) {
    public CoordinatorModeState {
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        systemPrompt = systemPrompt == null ? "" : systemPrompt.strip();
    }

    public static CoordinatorModeState disabled() {
        return new CoordinatorModeState(false, Set.of(), "");
    }
}
