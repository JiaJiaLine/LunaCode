package com.lunacode.subagent;

import java.util.Locale;

public enum AgentIsolation {
    NONE,
    WORKTREE;

    public static AgentIsolation fromFrontmatter(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if ("none".equals(normalized) || "false".equals(normalized)) {
            return NONE;
        }
        if ("worktree".equals(normalized)) {
            return WORKTREE;
        }
        throw new IllegalArgumentException("isolation 无效: " + value);
    }
}
