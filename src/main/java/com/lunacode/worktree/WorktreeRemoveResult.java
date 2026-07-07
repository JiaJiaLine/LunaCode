package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record WorktreeRemoveResult(
        String name,
        boolean removed,
        boolean kept,
        Optional<Path> path,
        Optional<String> branchName,
        WorktreeChanges changes,
        String message,
        List<String> warnings
) {
    public WorktreeRemoveResult {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        path = path == null ? Optional.empty() : path.map(value -> value.toAbsolutePath().normalize());
        branchName = branchName == null ? Optional.empty() : branchName;
        changes = changes == null ? WorktreeChanges.CLEAN : changes;
        message = message == null ? "" : message;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
