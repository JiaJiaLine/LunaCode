package com.lunacode.agent;

import com.lunacode.runtime.CancellationToken;

import com.lunacode.agent.event.AgentEventSink;

public interface AgentLoop {
    void run(AgentRequest request, AgentEventSink sink, CancellationToken cancellationToken);
}
