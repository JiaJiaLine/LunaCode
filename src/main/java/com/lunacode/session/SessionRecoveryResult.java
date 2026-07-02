package com.lunacode.session;

import com.lunacode.conversation.ConversationMessageSnapshot;

import java.util.List;
import java.util.Optional;

public record SessionRecoveryResult(
        String sessionId,
        List<ConversationMessageSnapshot> messages,
        List<String> warnings,
        Optional<ConversationMessageSnapshot> timeGapReminder,
        boolean compacted,
        boolean summaryOnly
) {
    public SessionRecoveryResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        timeGapReminder = timeGapReminder == null ? Optional.empty() : timeGapReminder;
    }
}
