package com.lunacode.worktree;

public interface WorktreeNameValidator {
    ValidWorktreeName validate(String name, WorktreeKind kind);
}
