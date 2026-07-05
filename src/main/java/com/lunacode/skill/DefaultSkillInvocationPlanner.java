package com.lunacode.skill;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DefaultSkillInvocationPlanner implements SkillInvocationPlanner {
    public static final String LOAD_SKILL_TOOL_NAME = "LoadSkill";

    private final SkillCatalog catalog;

    public DefaultSkillInvocationPlanner(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public SkillInvocationPlan plan(SkillInvocationRequest request) {
        if (catalog == null) {
            throw new IllegalStateException("Skill catalog 未配置");
        }
        SkillDefinition definition = catalog.loadForExecution(request.name())
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在或无效: " + request.name()));
        String renderedPrompt = definition.body().replace("$ARGUMENTS", request.rawArguments());
        Set<String> alwaysVisible = Set.of(LOAD_SKILL_TOOL_NAME);
        ToolAccessPolicy policy = definition.tools().isEmpty()
                ? ToolAccessPolicy.unrestricted(alwaysVisible)
                : ToolAccessPolicy.restricted(new LinkedHashSet<>(definition.tools()), alwaysVisible);
        return new SkillInvocationPlan(
                definition,
                renderedPrompt,
                policy,
                definition.model()
        );
    }
}
