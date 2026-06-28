package com.lunacode.conversation;

public record TokenUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer cacheReadInputTokens,
        Integer cacheCreationInputTokens,
        CacheUsageStatus cacheStatus
) {
    public TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        this(inputTokens, outputTokens, totalTokens, null, null, CacheUsageStatus.UNKNOWN);
    }

    public TokenUsage {
        cacheStatus = cacheStatus == null ? CacheUsageStatus.UNKNOWN : cacheStatus;
    }

    public static TokenUsage unknown() {
        return new TokenUsage(null, null, null, null, null, CacheUsageStatus.UNKNOWN);
    }

    public TokenUsage merge(TokenUsage newer) {
        if (newer == null) {
            return this;
        }
        return new TokenUsage(
                newer.inputTokens() != null ? newer.inputTokens() : inputTokens,
                newer.outputTokens() != null ? newer.outputTokens() : outputTokens,
                newer.totalTokens() != null ? newer.totalTokens() : totalTokens,
                newer.cacheReadInputTokens() != null ? newer.cacheReadInputTokens() : cacheReadInputTokens,
                newer.cacheCreationInputTokens() != null ? newer.cacheCreationInputTokens() : cacheCreationInputTokens,
                newer.cacheStatus() != CacheUsageStatus.UNKNOWN ? newer.cacheStatus() : cacheStatus
        );
    }
}
