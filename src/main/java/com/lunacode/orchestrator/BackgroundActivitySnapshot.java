package com.lunacode.orchestrator;

import java.time.Instant;

/**
 * 提供给展示层的后台活动只读快照。
 */
public record BackgroundActivitySnapshot(
        String id,
        String summary,
        Instant startedAt
) {
    public BackgroundActivitySnapshot {
        id = id == null ? "" : id;
        summary = summary == null ? "" : summary;
        startedAt = startedAt == null ? Instant.EPOCH : startedAt;
    }
}
