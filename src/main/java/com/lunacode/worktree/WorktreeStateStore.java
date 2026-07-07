package com.lunacode.worktree;

import java.nio.file.Path;

public interface WorktreeStateStore {
    WorktreeState load();

    void save(WorktreeState state);

    Path path();
}
