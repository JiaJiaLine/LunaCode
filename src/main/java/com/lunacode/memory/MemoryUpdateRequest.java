package com.lunacode.memory;

import com.lunacode.conversation.ConversationMessageSnapshot;

import java.time.Instant;
import java.util.List;

public record MemoryUpdateRequest(
        String sessionId,
        List<ConversationMessageSnapshot> turnDelta,
        MemoryIndexSnapshot currentIndex,
        Instant completedAt
) {
    public MemoryUpdateRequest {
        sessionId = sessionId == null ? "" : sessionId;
        turnDelta = turnDelta == null ? List.of() : List.copyOf(turnDelta);
        currentIndex = currentIndex == null ? MemoryIndexSnapshot.empty() : currentIndex;
        completedAt = completedAt == null ? Instant.now() : completedAt;
    }
}
