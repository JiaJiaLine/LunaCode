package com.lunacode.agent;

import com.lunacode.runtime.AgentRunConfig;

public record AgentRequest(
        String userMessage,
        AgentRunConfig config
) {
    public AgentRequest {
        userMessage = userMessage == null ? "" : userMessage.strip();
    }
}
