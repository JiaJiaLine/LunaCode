package com.lunacode.worktree;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WorktreeRecord(
        String name,
        WorktreeKind kind,
        Path path,
        String branchName,
        String baseRef,
        String headCommit,
        Optional<String> originalBranch,
        Instant createdAt,
        Instant lastUsedAt,
        List<String> warnings
) {
    public WorktreeRecord {
        name = requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        branchName = requireText(branchName, "branchName");
        baseRef = requireText(baseRef, "baseRef");
        headCommit = requireText(headCommit, "headCommit");
        originalBranch = originalBranch == null ? Optional.empty() : originalBranch;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        lastUsedAt = lastUsedAt == null ? createdAt : lastUsedAt;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public WorktreeRecord withHeadCommit(String newHeadCommit) {
        return new WorktreeRecord(name, kind, path, branchName, baseRef, newHeadCommit, originalBranch, createdAt, lastUsedAt, warnings);
    }

    public WorktreeRecord withLastUsedAt(Instant newLastUsedAt) {
        return new WorktreeRecord(name, kind, path, branchName, baseRef, headCommit, originalBranch, createdAt, newLastUsedAt, warnings);
    }

    public WorktreeRecord withWarnings(List<String> newWarnings) {
        return new WorktreeRecord(name, kind, path, branchName, baseRef, headCommit, originalBranch, createdAt, lastUsedAt, newWarnings);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
