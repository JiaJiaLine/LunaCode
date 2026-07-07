package com.lunacode.worktree;

public record WorktreeChanges(int uncommitted, int newCommits) {
    public static final WorktreeChanges CLEAN = new WorktreeChanges(0, 0);

    public WorktreeChanges {
        if (uncommitted < 0) {
            throw new IllegalArgumentException("uncommitted must be >= 0");
        }
        if (newCommits < 0) {
            throw new IllegalArgumentException("newCommits must be >= 0");
        }
    }

    public boolean hasChanges() {
        return uncommitted > 0 || newCommits > 0;
    }

    public String summary() {
        if (!hasChanges()) {
            return "clean";
        }
        return "uncommitted=" + uncommitted + ", newCommits=" + newCommits;
    }
}
