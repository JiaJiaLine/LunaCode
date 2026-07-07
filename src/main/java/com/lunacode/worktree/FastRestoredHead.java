package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record FastRestoredHead(
        Path worktreePath,
        Path gitDir,
        Optional<String> branchName,
        String headCommit,
        boolean detached
) {
    public FastRestoredHead {
        worktreePath = Objects.requireNonNull(worktreePath, "worktreePath").toAbsolutePath().normalize();
        gitDir = Objects.requireNonNull(gitDir, "gitDir").toAbsolutePath().normalize();
        branchName = branchName == null ? Optional.empty() : branchName;
        headCommit = requireText(headCommit, "headCommit");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
