package com.lunacode.session;

import com.lunacode.conversation.ConversationMessageSnapshot;

import java.util.List;

public record SessionLoadResult(
        SessionId id,
        SessionInfo info,
        List<ConversationMessageSnapshot> messages,
        List<String> warnings
) {
    public SessionLoadResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
