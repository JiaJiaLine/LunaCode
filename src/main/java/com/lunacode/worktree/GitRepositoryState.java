package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record GitRepositoryState(
        Path repoRoot,
        String headCommit,
        Optional<String> branchName,
        boolean dirty,
        String statusSummary
) {
    public GitRepositoryState {
        repoRoot = Objects.requireNonNull(repoRoot, "repoRoot").toAbsolutePath().normalize();
        headCommit = requireText(headCommit, "headCommit");
        branchName = branchName == null ? Optional.empty() : branchName;
        statusSummary = statusSummary == null ? "" : statusSummary;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
