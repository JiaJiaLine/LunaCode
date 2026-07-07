package com.lunacode.worktree;

public enum WorktreeKind {
    MANUAL,
    AGENT,
    WORKFLOW;

    public boolean isAutomatic() {
        return this == AGENT || this == WORKFLOW;
    }
}
