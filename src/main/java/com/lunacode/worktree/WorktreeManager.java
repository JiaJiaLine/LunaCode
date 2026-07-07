package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface WorktreeManager {
    WorktreeCreateResult create(WorktreeCreateRequest request);

    Optional<WorktreeRecord> find(String name);

    List<WorktreeSnapshot> list();

    WorktreeSession enter(String name, String sessionId);

    void exit();

    Optional<WorktreeSession> currentSession();

    Path effectiveWorkDir();

    WorktreeRemoveResult remove(WorktreeRemoveRequest request);

    WorktreeCleanupResult cleanupExpired(WorktreeCleanupPolicy policy);

    String generateAgentName();

    List<String> startupWarnings();
}
