package com.lunacode.context;

import com.lunacode.conversation.ConversationMessageSnapshot;

import java.util.List;

public record SessionLogSnapshot(
        List<ConversationMessageSnapshot> messages,
        List<ExternalizedToolResultRef> externalizedToolResults
) {
    public SessionLogSnapshot {
        messages = messages == null ? List.of() : List.copyOf(messages);
        externalizedToolResults = externalizedToolResults == null ? List.of() : List.copyOf(externalizedToolResults);
    }
}
