package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.Optional;

public interface WorktreeSessionStore {
    Optional<WorktreeSession> load();

    void save(Optional<WorktreeSession> session);

    Path path();
}
