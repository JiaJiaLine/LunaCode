package com.lunacode.subagent;

import com.lunacode.background.ProgressTracker;
import com.lunacode.runtime.CancellationToken;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultSubAgentRunHandle implements SubAgentRunHandle {
    private final String id;
    private final CompletableFuture<SubAgentResult> completion;
    private final CancellationToken cancellationToken;
    private final ProgressTracker progress;
    private final AtomicReference<String> backgroundTaskId = new AtomicReference<>("");

    public DefaultSubAgentRunHandle(CompletableFuture<SubAgentResult> completion, CancellationToken cancellationToken, ProgressTracker progress) {
        this("subagent-" + UUID.randomUUID(), completion, cancellationToken, progress);
    }

    public DefaultSubAgentRunHandle(String id, CompletableFuture<SubAgentResult> completion, CancellationToken cancellationToken, ProgressTracker progress) {
        this.id = id == null || id.isBlank() ? "subagent-" + UUID.randomUUID() : id;
        this.completion = Objects.requireNonNull(completion, "completion");
        this.cancellationToken = cancellationToken == null ? new CancellationToken() : cancellationToken;
        this.progress = progress == null ? new ProgressTracker() : progress;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CompletableFuture<SubAgentResult> completion() {
        return completion;
    }

    @Override
    public CancellationToken cancellationToken() {
        return cancellationToken;
    }

    @Override
    public ProgressTracker progress() {
        return progress;
    }

    @Override
    public void markAdoptedByBackground(String taskId) {
        backgroundTaskId.set(taskId == null ? "" : taskId);
    }

    @Override
    public java.util.Optional<String> backgroundTaskId() {
        String value = backgroundTaskId.get();
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }
}
