package com.lunacode.background;

import com.lunacode.subagent.DefaultSubAgentRunHandle;
import com.lunacode.subagent.SubAgentLaunchRequest;
import com.lunacode.subagent.SubAgentResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBackgroundTaskManagerTest {
    @Test
    void launchTracksCompletionAndNotifiesListener() throws Exception {
        CompletableFuture<SubAgentResult> completion = new CompletableFuture<>();
        DefaultSubAgentRunHandle handle = new DefaultSubAgentRunHandle(completion, null, new ProgressTracker());
        DefaultBackgroundTaskManager manager = new DefaultBackgroundTaskManager(request -> handle, Executors.newSingleThreadExecutor());
        AtomicReference<String> notified = new AtomicReference<>();
        manager.addListener(notified::set);

        String taskId = manager.launch(new SubAgentLaunchRequest(null, Optional.empty(), "task", true, null, null));
        assertEquals(BackgroundTaskStatus.RUNNING, manager.get(taskId).orElseThrow().status());

        completion.complete(new SubAgentResult("done", "full result", null, 0, false, Optional.empty()));
        Thread.sleep(100);

        BackgroundTaskSnapshot snapshot = manager.get(taskId).orElseThrow();
        assertEquals(BackgroundTaskStatus.COMPLETED, snapshot.status());
        assertEquals("full result", snapshot.result());
        assertEquals(taskId, notified.get());
    }

    @Test
    void adoptRunningDoesNotStartNewHandle() throws Exception {
        CompletableFuture<SubAgentResult> completion = new CompletableFuture<>();
        DefaultSubAgentRunHandle handle = new DefaultSubAgentRunHandle(completion, null, new ProgressTracker());
        DefaultBackgroundTaskManager manager = new DefaultBackgroundTaskManager(request -> {
            throw new AssertionError("adoptRunning 不应启动新 handle");
        }, Executors.newSingleThreadExecutor());

        String taskId = manager.adoptRunning(handle, "task");
        assertTrue(handle.backgroundTaskId().isPresent());
        completion.complete(new SubAgentResult("done", "adopted", null, 0, false, Optional.empty()));
        Thread.sleep(100);

        assertEquals(BackgroundTaskStatus.COMPLETED, manager.get(taskId).orElseThrow().status());
    }
}