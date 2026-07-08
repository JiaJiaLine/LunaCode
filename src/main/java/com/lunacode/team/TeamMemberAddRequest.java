package com.lunacode.team;

import java.util.Optional;

public record TeamMemberAddRequest(
        String teamName,
        String name,
        String role,
        TeamMemberBackendKind backend,
        Optional<String> agentType,
        boolean planModeRequired,
        Optional<String> agentId,
        String sessionId
) {
    public TeamMemberAddRequest {
        teamName = teamName == null ? "" : teamName.strip();
        name = name == null ? "" : name.strip();
        role = role == null || role.isBlank() ? "teammate" : role.strip();
        backend = backend == null ? TeamMemberBackendKind.SAME_PROCESS : backend;
        agentType = agentType == null ? Optional.empty() : agentType.map(String::strip).filter(value -> !value.isBlank());
        agentId = agentId == null ? Optional.empty() : agentId.map(String::strip).filter(value -> !value.isBlank());
        sessionId = sessionId == null ? "" : sessionId.strip();
    }
}
