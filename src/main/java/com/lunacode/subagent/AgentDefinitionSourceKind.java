package com.lunacode.subagent;

public enum AgentDefinitionSourceKind {
    PLUGIN(0),
    BUILTIN(100),
    USER(200),
    PROJECT(300);

    private final int priority;

    AgentDefinitionSourceKind(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
