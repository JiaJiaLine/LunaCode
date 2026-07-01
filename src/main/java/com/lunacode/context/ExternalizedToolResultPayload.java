package com.lunacode.context;

public record ExternalizedToolResultPayload(
        String messageId,
        String toolUseId,
        String toolName,
        String content,
        boolean error
) {}
