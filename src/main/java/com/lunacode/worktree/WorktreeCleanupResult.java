package com.lunacode.worktree;

import java.util.List;

public record WorktreeCleanupResult(
        int scanned,
        int removed,
        int kept,
        int skipped,
        List<WorktreeRemoveResult> removals,
        List<WorktreeSnapshot> keptWorktrees,
        List<String> warnings
) {
    public WorktreeCleanupResult {
        if (scanned < 0 || removed < 0 || kept < 0 || skipped < 0) {
            throw new IllegalArgumentException("cleanup counters must be >= 0");
        }
        removals = removals == null ? List.of() : List.copyOf(removals);
        keptWorktrees = keptWorktrees == null ? List.of() : List.copyOf(keptWorktrees);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
