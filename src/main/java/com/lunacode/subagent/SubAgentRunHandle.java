package com.lunacode.subagent;

import com.lunacode.background.ProgressTracker;
import com.lunacode.runtime.CancellationToken;

import java.util.concurrent.CompletableFuture;

public interface SubAgentRunHandle {
    String id();

    CompletableFuture<SubAgentResult> completion();

    CancellationToken cancellationToken();

    ProgressTracker progress();

    void markAdoptedByBackground(String taskId);

    java.util.Optional<String> backgroundTaskId();
}
