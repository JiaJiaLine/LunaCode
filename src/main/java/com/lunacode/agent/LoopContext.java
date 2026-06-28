package com.lunacode.agent;

import com.lunacode.runtime.CancellationToken;

import com.lunacode.runtime.AgentRunConfig;

import com.lunacode.conversation.TokenUsage;

public record LoopContext(
        AgentRunConfig config,
        CancellationToken cancellationToken,
        int currentIteration,
        int consecutiveUnknownToolCount,
        TokenUsage cumulativeUsage
) {}
