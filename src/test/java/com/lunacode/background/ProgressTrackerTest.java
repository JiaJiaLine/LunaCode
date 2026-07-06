package com.lunacode.background;

import com.lunacode.conversation.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressTrackerTest {
    @Test
    void tracksToolCallsUsageAndRecentActivity() {
        ProgressTracker tracker = new ProgressTracker();

        tracker.recordToolCall("ReadFile");
        tracker.recordToolCall("Bash");
        tracker.recordUsage(new TokenUsage(10, 2, 12));
        tracker.recordUsage(new TokenUsage(null, 3, 13));
        tracker.recordActivity("done");

        assertEquals(2, tracker.toolCallCount());
        assertEquals(10, tracker.usage().inputTokens());
        assertEquals(3, tracker.usage().outputTokens());
        assertEquals(13, tracker.usage().totalTokens());
        assertEquals("done", tracker.recentActivity());
        assertNotNull(tracker.lastActivityAt());
    }
}