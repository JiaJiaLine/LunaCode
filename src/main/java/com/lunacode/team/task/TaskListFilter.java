package com.lunacode.team.task;

import java.util.Optional;

public record TaskListFilter(
        Optional<TeamTaskStatus> status,
        Optional<String> assignee,
        boolean onlyClaimable
) {
    public TaskListFilter {
        status = status == null ? Optional.empty() : status;
        assignee = assignee == null ? Optional.empty() : assignee.map(String::strip).filter(value -> !value.isBlank());
    }

    public static TaskListFilter all() {
        return new TaskListFilter(Optional.empty(), Optional.empty(), false);
    }
}
