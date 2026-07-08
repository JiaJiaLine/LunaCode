package com.lunacode.team;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record AgentNameRegistry(Map<String, String> names) {
    public AgentNameRegistry {
        TeamNameValidator validator = new TeamNameValidator();
        Map<String, String> copy = new LinkedHashMap<>();
        if (names != null) {
            names.forEach((name, agentId) -> {
                String safeName = validator.validate(name, "agentName");
                String safeAgentId = agentId == null ? "" : agentId.strip();
                if (safeAgentId.isBlank()) {
                    throw new IllegalArgumentException("agentId must not be blank");
                }
                copy.put(safeName, safeAgentId);
            });
        }
        names = Collections.unmodifiableMap(copy);
    }

    public static AgentNameRegistry empty() {
        return new AgentNameRegistry(Map.of());
    }

    public AgentNameRegistry register(String name, String agentId) {
        TeamNameValidator validator = new TeamNameValidator();
        String safeName = validator.validate(name, "agentName");
        String safeAgentId = agentId == null ? "" : agentId.strip();
        if (safeAgentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        String existing = names.get(safeName);
        if (existing != null && !existing.equals(safeAgentId)) {
            throw new IllegalArgumentException("agent name already registered: " + safeName);
        }
        Map<String, String> copy = new LinkedHashMap<>(names);
        copy.put(safeName, safeAgentId);
        return new AgentNameRegistry(copy);
    }

    public AgentNameRegistry rebind(String name, String agentId) {
        TeamNameValidator validator = new TeamNameValidator();
        String safeName = validator.validate(name, "agentName");
        String safeAgentId = agentId == null ? "" : agentId.strip();
        if (safeAgentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        Map<String, String> copy = new LinkedHashMap<>(names);
        copy.put(safeName, safeAgentId);
        return new AgentNameRegistry(copy);
    }

    public Optional<String> resolveName(String target) {
        String safeTarget = target == null ? "" : target.strip();
        if (safeTarget.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(names.get(safeTarget));
    }

    public String resolveNameOrAgentId(String target) {
        String safeTarget = target == null ? "" : target.strip();
        return resolveName(safeTarget).orElse(safeTarget);
    }
}
