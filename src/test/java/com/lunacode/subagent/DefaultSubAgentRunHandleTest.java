package com.lunacode.subagent;

import com.lunacode.background.ProgressTracker;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSubAgentRunHandleTest {
    @Test
    void recordsBackgroundTaskAdoption() {
        DefaultSubAgentRunHandle handle = new DefaultSubAgentRunHandle(
                "child-1",
                new CompletableFuture<>(),
                null,
                new ProgressTracker()
        );

        assertFalse(handle.backgroundTaskId().isPresent());

        handle.markAdoptedByBackground("bg-1");

        assertEquals(Optional.of("bg-1"), handle.backgroundTaskId());
        assertTrue(handle.id().equals("child-1"));
    }
}