package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.List;

public interface WorktreeEnvironmentInitializer {
    List<String> initialize(Path repoRoot, Path worktreePath);
}
