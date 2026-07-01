package com.lunacode.conversation;

import java.time.Instant;
import java.util.List;

public record ConversationMessageSnapshot(
        String id,
        MessageRole role,
        MessageStatus status,
        Instant timestamp,
        TokenUsage usage,
        String content,
        List<ContentBlock> blocks,
        ConversationMessageMetadata metadata,
        String errorSummary
) {
    public ConversationMessageSnapshot {
        content = content == null ? "" : content;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        metadata = metadata == null ? ConversationMessageMetadata.empty() : metadata;
        usage = usage == null ? TokenUsage.unknown() : usage;
    }
}
