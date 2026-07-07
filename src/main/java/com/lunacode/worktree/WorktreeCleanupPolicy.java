package com.lunacode.worktree;

import java.time.Duration;
import java.time.Instant;

public record WorktreeCleanupPolicy(Duration ttl, Instant now, boolean dryRun) {
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public WorktreeCleanupPolicy {
        ttl = ttl == null ? DEFAULT_TTL : ttl;
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        now = now == null ? Instant.now() : now;
    }

    public static WorktreeCleanupPolicy defaults() {
        return new WorktreeCleanupPolicy(DEFAULT_TTL, Instant.now(), false);
    }
}
