package com.lunacode.agent;

public interface AgentEventSink {
    void emit(AgentEvent event);
}
