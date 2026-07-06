package com.lunacode.background;

import com.lunacode.conversation.TokenUsage;

import java.time.Instant;

public record BackgroundTaskSnapshot(
        String id,
        String task,
        BackgroundTaskStatus status,
        String result,
        String failureReason,
        Instant startTime,
        Instant endTime,
        int toolCallCount,
        TokenUsage usage,
        String recentActivity,
        Instant lastActivityAt
) {
    public BackgroundTaskSnapshot {
        id = id == null ? "" : id;
        task = task == null ? "" : task;
        status = status == null ? BackgroundTaskStatus.RUNNING : status;
        result = result == null ? "" : result;
        failureReason = failureReason == null ? "" : failureReason;
        usage = usage == null ? TokenUsage.unknown() : usage;
        recentActivity = recentActivity == null ? "" : recentActivity;
    }
}
