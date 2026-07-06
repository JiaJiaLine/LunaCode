package com.lunacode.background;

import com.lunacode.subagent.DefaultSubAgentRunHandle;
import com.lunacode.subagent.SubAgentResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultForegroundSubAgentTrackerTest {
    @Test
    void adoptsCurrentHandleIntoBackgroundManager() throws Exception {
        CompletableFuture<SubAgentResult> completion = new CompletableFuture<>();
        DefaultSubAgentRunHandle handle = new DefaultSubAgentRunHandle("child-1", completion, null, new ProgressTracker());
        DefaultBackgroundTaskManager manager = new DefaultBackgroundTaskManager(request -> {
            throw new AssertionError("adopt should not start a new handle");
        }, Executors.newSingleThreadExecutor());
        DefaultForegroundSubAgentTracker tracker = new DefaultForegroundSubAgentTracker(manager);

        tracker.setCurrent(handle, "foreground task");
        Optional<String> taskId = tracker.adoptCurrentToBackground();

        assertTrue(taskId.isPresent());
        assertTrue(tracker.current().isEmpty());
        assertEquals(taskId.get(), handle.backgroundTaskId().orElseThrow());

        completion.complete(new SubAgentResult("done", "foreground result", null, 0, false, Optional.empty()));
        Thread.sleep(100);

        assertEquals(BackgroundTaskStatus.COMPLETED, manager.get(taskId.get()).orElseThrow().status());
    }
}