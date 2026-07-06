package com.lunacode.background;

import com.lunacode.conversation.TokenUsage;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ProgressTracker {
    private final AtomicInteger toolCallCount = new AtomicInteger();
    private final AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.unknown());
    private final AtomicReference<String> recentActivity = new AtomicReference<>("");
    private final AtomicReference<Instant> lastActivityAt = new AtomicReference<>(Instant.now());

    public void recordToolCall(String toolName) {
        toolCallCount.incrementAndGet();
        recordActivity("工具调用: " + safe(toolName));
    }

    public void recordUsage(TokenUsage newer) {
        if (newer != null) {
            usage.updateAndGet(current -> current.merge(newer));
        }
        lastActivityAt.set(Instant.now());
    }

    public void recordActivity(String activity) {
        recentActivity.set(safe(activity));
        lastActivityAt.set(Instant.now());
    }

    public int toolCallCount() {
        return toolCallCount.get();
    }

    public TokenUsage usage() {
        return usage.get();
    }

    public String recentActivity() {
        return recentActivity.get();
    }

    public Instant lastActivityAt() {
        return lastActivityAt.get();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
