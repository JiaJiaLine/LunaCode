package com.lunacode.subagent;

import java.util.Optional;

public record AgentToolRequest(
        String task,
        Optional<String> subagentType,
        boolean runInBackground
) {
    public AgentToolRequest {
        task = task == null ? "" : task.strip();
        subagentType = subagentType == null ? Optional.empty() : subagentType.map(String::strip).filter(value -> !value.isBlank());
    }
}
