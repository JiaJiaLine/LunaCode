package com.lunacode.memory;

import java.nio.file.Path;
import java.time.Instant;

public record MemoryNote(
        String id,
        MemoryType type,
        String title,
        Instant createdAt,
        Instant updatedAt,
        String sourceSession,
        String body,
        Path path
) {
    public MemoryNote {
        title = title == null ? "" : title;
        sourceSession = sourceSession == null ? "" : sourceSession;
        body = body == null ? "" : body;
    }
}
