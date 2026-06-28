package com.lunacode.agent.turn;

import com.lunacode.prompt.PromptBundle;

import com.lunacode.agent.event.AgentEventSink;

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
