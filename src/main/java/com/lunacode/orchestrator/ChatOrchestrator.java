package com.lunacode.orchestrator;

public interface ChatOrchestrator {
    void submitUserMessage(String content);

    void cancelCurrentRun();

    StatusSnapshot status();
}
