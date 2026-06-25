package com.lunacode.conversation;

import java.time.Instant;

public record InternalMessage(
        String id,
        MessageRole role,
        MessageStatus status,
        Instant timestamp,
        TokenUsage usage,
        String content,
        String errorSummary
) {}