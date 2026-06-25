package com.lunacode.conversation;

public record TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    public static TokenUsage unknown() {
        return new TokenUsage(null, null, null);
    }

    public TokenUsage merge(TokenUsage newer) {
        if (newer == null) {
            return this;
        }
        return new TokenUsage(
                newer.inputTokens() != null ? newer.inputTokens() : inputTokens,
                newer.outputTokens() != null ? newer.outputTokens() : outputTokens,
                newer.totalTokens() != null ? newer.totalTokens() : totalTokens
        );
    }
}