package com.lunacode.team.task;

import java.util.Optional;

public record TaskCreateRequest(
        String title,
        String description,
        Optional<String> assignee
) {
    public TaskCreateRequest {
        title = title == null ? "" : title.strip();
        description = description == null ? "" : description.strip();
        assignee = assignee == null ? Optional.empty() : assignee.map(String::strip).filter(value -> !value.isBlank());
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }
}
