package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.List;

public interface WorktreeIncludeMatcher {
    List<Path> match(Path repoRoot, List<Path> ignoredFiles);
}
