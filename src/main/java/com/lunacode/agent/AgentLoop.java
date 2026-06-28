package com.lunacode.agent;

public interface AgentLoop {
    void run(AgentRequest request, AgentEventSink sink, CancellationToken cancellationToken);
}
