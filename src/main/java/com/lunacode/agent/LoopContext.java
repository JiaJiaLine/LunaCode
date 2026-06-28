package com.lunacode.agent;

import com.lunacode.conversation.TokenUsage;

public record LoopContext(
        AgentRunConfig config,
        CancellationToken cancellationToken,
        int currentIteration,
        int consecutiveUnknownToolCount,
        TokenUsage cumulativeUsage
) {}
