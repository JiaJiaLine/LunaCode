package com.lunacode.agent;

public record AgentRequest(
        String userMessage,
        AgentRunConfig config
) {
    public AgentRequest {
        userMessage = userMessage == null ? "" : userMessage.strip();
    }
}
