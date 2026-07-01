package com.lunacode.context;

public record TokenEstimate(
        long estimatedTokens,
        long estimatedChars,
        String source
) {}
