package com.lunacode.subagent;

import java.util.Optional;

public record AgentToolRequest(
        String task,
        Optional<String> subagentType,
        boolean runInBackground,
        Optional<String> name,
        Optional<String> teamName,
        Optional<String> role,
        Optional<String> backend,
        boolean planModeRequired
) {
    public AgentToolRequest(String task, Optional<String> subagentType, boolean runInBackground) {
        this(task, subagentType, runInBackground, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    public AgentToolRequest {
        task = task == null ? "" : task.strip();
        subagentType = subagentType == null ? Optional.empty() : subagentType.map(String::strip).filter(value -> !value.isBlank());
        name = name == null ? Optional.empty() : name.map(String::strip).filter(value -> !value.isBlank());
        teamName = teamName == null ? Optional.empty() : teamName.map(String::strip).filter(value -> !value.isBlank());
        role = role == null ? Optional.empty() : role.map(String::strip).filter(value -> !value.isBlank());
        backend = backend == null ? Optional.empty() : backend.map(String::strip).filter(value -> !value.isBlank());
    }
}