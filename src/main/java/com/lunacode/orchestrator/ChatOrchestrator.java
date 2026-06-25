package com.lunacode.orchestrator;

public interface ChatOrchestrator {
    void submitUserMessage(String content);

    StatusSnapshot status();
}