package com.lunacode.worktree;

import java.util.Objects;

public record WorktreeRemoveRequest(
        String name,
        boolean discardChanges,
        boolean automaticCleanup
) {
    public WorktreeRemoveRequest {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public static WorktreeRemoveRequest manual(String name, boolean discardChanges) {
        return new WorktreeRemoveRequest(name, discardChanges, false);
    }

    public static WorktreeRemoveRequest automatic(String name) {
        return new WorktreeRemoveRequest(name, false, true);
    }
}
