package com.lunacode.skill;

import java.util.Optional;

public record SkillInvocationPlan(
        SkillDefinition definition,
        String renderedPrompt,
        ToolAccessPolicy toolAccessPolicy,
        Optional<String> modelOverride
) {
    public SkillInvocationPlan {
        if (definition == null) {
            throw new IllegalArgumentException("definition is required");
        }
        renderedPrompt = renderedPrompt == null ? "" : renderedPrompt;
        toolAccessPolicy = toolAccessPolicy == null ? ToolAccessPolicy.unrestricted(java.util.Set.of("LoadSkill")) : toolAccessPolicy;
        modelOverride = modelOverride == null ? Optional.empty() : modelOverride.map(String::strip).filter(value -> !value.isBlank());
    }
}
