package com.lunacode.context;

import java.nio.file.Path;
import java.time.Instant;

public record RecentFileAccess(
        Path path,
        String toolName,
        Instant accessedAt,
        long observedSizeBytes
) {}
