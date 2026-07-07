package com.lunacode.worktree;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record WorktreeSession(
        String worktreeName,
        Path originalCwd,
        Path worktreePath,
        String worktreeBranch,
        Optional<String> originalBranch,
        String originalHeadCommit,
        String sessionId,
        Instant enteredAt
) {
    public WorktreeSession {
        worktreeName = requireText(worktreeName, "worktreeName");
        originalCwd = Objects.requireNonNull(originalCwd, "originalCwd").toAbsolutePath().normalize();
        worktreePath = Objects.requireNonNull(worktreePath, "worktreePath").toAbsolutePath().normalize();
        worktreeBranch = requireText(worktreeBranch, "worktreeBranch");
        originalBranch = originalBranch == null ? Optional.empty() : originalBranch;
        originalHeadCommit = requireText(originalHeadCommit, "originalHeadCommit");
        sessionId = requireText(sessionId, "sessionId");
        enteredAt = enteredAt == null ? Instant.now() : enteredAt;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
