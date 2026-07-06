package com.lunacode.subagent;

import com.lunacode.conversation.TokenUsage;

import java.util.Optional;

public record SubAgentResult(
        String summary,
        String fullResult,
        TokenUsage usage,
        int toolCallCount,
        boolean reachedMaxTurns,
        Optional<String> failureReason
) {
    public SubAgentResult {
        summary = summary == null ? "" : summary;
        fullResult = fullResult == null ? "" : fullResult;
        usage = usage == null ? TokenUsage.unknown() : usage;
        failureReason = failureReason == null ? Optional.empty() : failureReason.map(String::strip).filter(value -> !value.isBlank());
    }
}
