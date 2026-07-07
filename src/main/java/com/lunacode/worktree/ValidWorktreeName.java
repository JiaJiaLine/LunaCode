package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.Objects;

public record ValidWorktreeName(
        String rawName,
        WorktreeKind kind,
        Path relativePath,
        String branchSlug,
        String branchName
) {
    public ValidWorktreeName {
        rawName = requireText(rawName, "rawName");
        kind = Objects.requireNonNull(kind, "kind");
        relativePath = Objects.requireNonNull(relativePath, "relativePath").normalize();
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("relativePath must be relative");
        }
        branchSlug = requireText(branchSlug, "branchSlug");
        branchName = requireText(branchName, "branchName");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
