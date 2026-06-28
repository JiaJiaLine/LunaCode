package com.lunacode.agent.event;

public interface AgentEventSink {
    void emit(AgentEvent event);
}
