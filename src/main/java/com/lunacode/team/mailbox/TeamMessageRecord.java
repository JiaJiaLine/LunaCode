package com.lunacode.team.mailbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TeamMessageRecord(
        String id,
        TeamMessageType type,
        String from,
        String to,
        String summary,
        String message,
        Instant timestamp,
        boolean read,
        Map<String, String> metadata
) {
    public TeamMessageRecord {
        id = id == null || id.isBlank() ? "msg-" + UUID.randomUUID() : id.strip();
        type = type == null ? TeamMessageType.TEXT : type;
        from = from == null ? "" : from.strip();
        to = to == null ? "" : to.strip();
        summary = summary == null ? "" : summary.strip();
        message = message == null ? "" : message.strip();
        timestamp = timestamp == null ? Instant.now() : timestamp;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static TeamMessageRecord text(String from, String to, String summary, String message) {
        return new TeamMessageRecord(null, TeamMessageType.TEXT, from, to, summary, message, Instant.now(), false, Map.of());
    }
}
