package com.lunacode.team.task;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public record TeamTaskRecord(
        String id,
        String title,
        String description,
        TeamTaskStatus status,
        Optional<String> assignee,
        Set<String> blockedBy,
        Set<String> blocks,
        Instant createdAt,
        Instant updatedAt
) {
    public TeamTaskRecord {
        id = requireText(id, "id");
        title = requireText(title, "title");
        description = description == null ? "" : description.strip();
        status = status == null ? TeamTaskStatus.TODO : status;
        assignee = assignee == null ? Optional.empty() : assignee.map(String::strip).filter(value -> !value.isBlank());
        blockedBy = normalizeSet(blockedBy);
        blocks = normalizeSet(blocks);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public boolean blocked() {
        return !blockedBy.isEmpty() && status != TeamTaskStatus.DONE && status != TeamTaskStatus.CANCELLED;
    }

    public boolean claimable() {
        return status == TeamTaskStatus.TODO && assignee.isEmpty() && !blocked();
    }

    public TeamTaskRecord withStatus(TeamTaskStatus newStatus) {
        return new TeamTaskRecord(id, title, description, newStatus, assignee, blockedBy, blocks, createdAt, Instant.now());
    }

    public TeamTaskRecord withAssignee(Optional<String> newAssignee) {
        return new TeamTaskRecord(id, title, description, status, newAssignee, blockedBy, blocks, createdAt, Instant.now());
    }

    public TeamTaskRecord withDependencies(Set<String> newBlockedBy, Set<String> newBlocks) {
        return new TeamTaskRecord(id, title, description, status, assignee, newBlockedBy, newBlocks, createdAt, Instant.now());
    }

    private static Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        values.stream()
                .map(value -> value == null ? "" : value.strip())
                .filter(value -> !value.isBlank())
                .forEach(copy::add);
        return Set.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        String text = value == null ? "" : value.strip();
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return text;
    }
}
