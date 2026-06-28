package com.lunacode.agent;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.TokenUsage;

import java.util.List;

public record AgentTurnInput(
        int turnIndex,
        String systemPrompt,
        PromptBundle promptBundle,
        List<ApiMessage> messages,
        ProviderConfig providerConfig,
        ArrayNode enabledTools,
        TokenUsage cumulativeUsage,
        AgentEventSink sink
) {
    public AgentTurnInput(
            int turnIndex,
            String systemPrompt,
            List<ApiMessage> messages,
            ProviderConfig providerConfig,
            ArrayNode enabledTools,
            TokenUsage cumulativeUsage,
            AgentEventSink sink
    ) {
        this(turnIndex, systemPrompt, null, messages, providerConfig, enabledTools, cumulativeUsage, sink);
    }
}
