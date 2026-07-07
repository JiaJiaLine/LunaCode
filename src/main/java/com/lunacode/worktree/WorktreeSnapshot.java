package com.lunacode.worktree;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record WorktreeSnapshot(
        String name,
        WorktreeKind kind,
        Path path,
        String branchName,
        String headCommit,
        boolean current,
        WorktreeChanges changes,
        Instant createdAt,
        Instant lastUsedAt,
        List<String> warnings
) {
    public WorktreeSnapshot {
        name = requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        branchName = requireText(branchName, "branchName");
        headCommit = requireText(headCommit, "headCommit");
        changes = changes == null ? WorktreeChanges.CLEAN : changes;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        lastUsedAt = lastUsedAt == null ? createdAt : lastUsedAt;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
