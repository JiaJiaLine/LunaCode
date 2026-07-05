package com.lunacode.agent;

import com.lunacode.runtime.AgentRunConfig;

public record AgentRequest(
        String userMessage,
        String modelMessage,
        AgentRunConfig config
) {
    public AgentRequest(String userMessage, AgentRunConfig config) {
        this(userMessage, userMessage, config);
    }

    public AgentRequest {
        userMessage = userMessage == null ? "" : userMessage.strip();
        modelMessage = modelMessage == null ? userMessage : modelMessage;
        if (modelMessage.isBlank()) {
            modelMessage = userMessage;
        }
    }
}