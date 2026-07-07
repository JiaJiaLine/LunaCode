package com.lunacode.worktree;

import java.util.List;
import java.util.Objects;

public record WorktreeCreateResult(
        WorktreeRecord record,
        boolean fastRestored,
        boolean created,
        String message,
        List<String> warnings
) {
    public WorktreeCreateResult {
        record = Objects.requireNonNull(record, "record");
        message = message == null ? "" : message;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
