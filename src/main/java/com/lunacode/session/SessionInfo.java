package com.lunacode.session;

import java.nio.file.Path;
import java.time.Instant;

public record SessionInfo(
        String id,
        Path path,
        String title,
        int messageCount,
        Instant createdAt,
        Instant lastActiveAt,
        boolean expired
) {}
