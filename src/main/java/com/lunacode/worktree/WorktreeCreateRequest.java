package com.lunacode.worktree;

import java.util.Objects;
import java.util.Optional;

public record WorktreeCreateRequest(
        String name,
        WorktreeKind kind,
        boolean allowExisting,
        Optional<String> sessionId
) {
    public WorktreeCreateRequest {
        name = requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        sessionId = sessionId == null ? Optional.empty() : sessionId;
    }

    public static WorktreeCreateRequest manual(String name) {
        return new WorktreeCreateRequest(name, WorktreeKind.MANUAL, true, Optional.empty());
    }

    public static WorktreeCreateRequest automatic(String name, WorktreeKind kind, String sessionId) {
        if (!kind.isAutomatic()) {
            throw new IllegalArgumentException("automatic worktree kind must be AGENT or WORKFLOW");
        }
        return new WorktreeCreateRequest(name, kind, true, Optional.ofNullable(sessionId));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
