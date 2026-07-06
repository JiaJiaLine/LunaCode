package com.lunacode.subagent;

import com.lunacode.config.AgentConfig;
import com.lunacode.runtime.AgentRunConfig;

import java.util.Locale;
import java.util.Optional;

public final class SubAgentModelResolver {
    private final AgentConfig agentConfig;

    public SubAgentModelResolver(AgentConfig agentConfig) {
        this.agentConfig = agentConfig == null ? AgentConfig.defaults() : agentConfig;
    }

    public Optional<String> resolve(AgentDefinition definition, AgentRunConfig parentConfig) {
        String model = definition == null ? "inherit" : definition.model();
        String normalized = model == null ? "inherit" : model.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.equals("inherit")) {
            return parentConfig == null ? Optional.empty() : parentConfig.modelOverride();
        }
        if (normalized.equals("sonnet") || normalized.equals("opus") || normalized.equals("haiku")) {
            String mapped = agentConfig.modelAliases().get(normalized);
            if (mapped == null || mapped.isBlank()) {
                throw new IllegalArgumentException("模型别名未配置: " + normalized);
            }
            return Optional.of(mapped);
        }
        return Optional.of(model.strip());
    }
}
