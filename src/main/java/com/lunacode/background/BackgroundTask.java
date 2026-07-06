package com.lunacode.background;

import com.lunacode.subagent.SubAgentRunHandle;
import com.lunacode.runtime.CancellationToken;

import java.time.Instant;
import java.util.Objects;

public final class BackgroundTask {
    private final String id;
    private final SubAgentRunHandle subAgent;
    private final String task;
    private final Instant startTime;
    private final CancellationToken cancellationToken;
    private final ProgressTracker progress;
    private volatile BackgroundTaskStatus status;
    private volatile String result;
    private volatile String failureReason;
    private volatile Instant endTime;

    public BackgroundTask(String id, SubAgentRunHandle subAgent, String task) {
        this.id = Objects.requireNonNull(id, "id");
        this.subAgent = Objects.requireNonNull(subAgent, "subAgent");
        this.task = task == null ? "" : task;
        this.startTime = Instant.now();
        this.cancellationToken = subAgent.cancellationToken();
        this.progress = subAgent.progress();
        this.status = BackgroundTaskStatus.RUNNING;
        this.result = "";
        this.failureReason = "";
    }

    public String id() {
        return id;
    }

    public void complete(String result) {
        this.result = result == null ? "" : result;
        this.failureReason = "";
        this.status = BackgroundTaskStatus.COMPLETED;
        this.endTime = Instant.now();
    }

    public void fail(String reason) {
        this.failureReason = reason == null ? "" : reason;
        this.result = this.failureReason;
        this.status = BackgroundTaskStatus.FAILED;
        this.endTime = Instant.now();
    }

    public BackgroundTaskSnapshot snapshot() {
        return new BackgroundTaskSnapshot(
                id,
                task,
                status,
                result,
                failureReason,
                startTime,
                endTime,
                progress.toolCallCount(),
                progress.usage(),
                progress.recentActivity(),
                progress.lastActivityAt()
        );
    }

    public CancellationToken cancellationToken() {
        return cancellationToken;
    }
}
