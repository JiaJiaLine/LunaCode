package com.lunacode.background;

import com.lunacode.conversation.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskNotificationFormatterTest {
    @Test
    void formatsCompletedTaskWithoutIntermediateTranscript() {
        BackgroundTaskSnapshot snapshot = new BackgroundTaskSnapshot(
                "bg-1",
                "review task",
                BackgroundTaskStatus.COMPLETED,
                "final result line 1\nfinal result line 2",
                "",
                Instant.now(),
                Instant.now(),
                2,
                new TokenUsage(10, 5, 15),
                "secret-intermediate-tool-transcript",
                Instant.now()
        );

        String formatted = new TaskNotificationFormatter().format(snapshot);

        assertTrue(formatted.startsWith("<task-notification>"));
        assertTrue(formatted.contains("id: bg-1"));
        assertTrue(formatted.contains("status: completed"));
        assertTrue(formatted.contains("tool_calls: 2"));
        assertTrue(formatted.contains("tokens: 15"));
        assertTrue(formatted.contains("final result line 1"));
        assertTrue(formatted.endsWith("</task-notification>"));
        assertFalse(formatted.contains("secret-intermediate-tool-transcript"));
    }

    @Test
    void formatsFailedTaskWithReason() {
        BackgroundTaskSnapshot snapshot = new BackgroundTaskSnapshot(
                "bg-2",
                "review task",
                BackgroundTaskStatus.FAILED,
                "",
                "boom",
                Instant.now(),
                Instant.now(),
                0,
                TokenUsage.unknown(),
                "",
                Instant.now()
        );

        String formatted = new TaskNotificationFormatter().format(snapshot);

        assertTrue(formatted.contains("status: failed"));
        assertTrue(formatted.contains("boom"));
    }
}