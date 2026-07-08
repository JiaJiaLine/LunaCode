package com.lunacode.team.task;

import java.util.List;
import java.util.Optional;

public interface TeamTaskStore {
    TeamTaskRecord create(TaskCreateRequest request);

    Optional<TeamTaskRecord> get(String id);

    List<TeamTaskRecord> list(TaskListFilter filter);

    TeamTaskRecord update(String id, TaskUpdatePatch patch, String actor);
}
