package com.lunacode.team.task;

import java.util.Optional;
import java.util.Set;

public record TaskUpdatePatch(
        Optional<TeamTaskStatus> status,
        Optional<String> assignee,
        boolean clearAssignee,
        Set<String> addBlocks,
        Set<String> addBlockedBy,
        boolean claim
) {
    public TaskUpdatePatch {
        status = status == null ? Optional.empty() : status;
        assignee = assignee == null ? Optional.empty() : assignee.map(String::strip).filter(value -> !value.isBlank());
        addBlocks = addBlocks == null ? Set.of() : Set.copyOf(addBlocks);
        addBlockedBy = addBlockedBy == null ? Set.of() : Set.copyOf(addBlockedBy);
    }

    public static TaskUpdatePatch empty() {
        return new TaskUpdatePatch(Optional.empty(), Optional.empty(), false, Set.of(), Set.of(), false);
    }
}
