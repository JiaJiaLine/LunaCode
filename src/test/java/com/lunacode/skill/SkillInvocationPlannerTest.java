package com.lunacode.skill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillInvocationPlannerTest {
    @Test
    void replacesAllArgumentsAndKeepsLoadSkillVisible() {
        SkillDefinition definition = definition("body $ARGUMENTS / $ARGUMENTS", List.of("ReadFile"), Optional.of("luna-small"));
        SkillInvocationPlanner planner = new DefaultSkillInvocationPlanner(new FixedCatalog(definition));

        SkillInvocationPlan plan = planner.plan(new SkillInvocationRequest("commit", "重点关注安全", SkillInvocationTrigger.SLASH));

        assertEquals("body 重点关注安全 / 重点关注安全", plan.renderedPrompt());
        assertTrue(plan.toolAccessPolicy().isRestricted());
        assertTrue(plan.toolAccessPolicy().allows("ReadFile"));
        assertTrue(plan.toolAccessPolicy().allows("LoadSkill"));
        assertTrue(plan.modelOverride().isPresent());
        assertEquals("luna-small", plan.modelOverride().orElseThrow());
    }

    @Test
    void emptyArgumentsBecomeEmptyStringAndNoToolsMeansUnrestricted() {
        SkillDefinition definition = definition("body [$ARGUMENTS]", List.of(), Optional.empty());
        SkillInvocationPlanner planner = new DefaultSkillInvocationPlanner(new FixedCatalog(definition));

        SkillInvocationPlan plan = planner.plan(new SkillInvocationRequest("commit", "", SkillInvocationTrigger.SLASH));

        assertEquals("body []", plan.renderedPrompt());
        assertTrue(plan.toolAccessPolicy().allows("Bash"));
        assertTrue(plan.modelOverride().isEmpty());
    }

    private SkillDefinition definition(String body, List<String> tools, Optional<String> model) {
        return new SkillDefinition(
                "commit",
                "提交",
                SkillExecutionMode.INLINE,
                SkillContextPolicy.FULL,
                model,
                tools,
                body,
                new SkillOrigin(SkillSourceKind.PROJECT, "test", Optional.empty(), 300),
                Optional.empty()
        );
    }

    private record FixedCatalog(SkillDefinition definition) implements SkillCatalog {
        @Override
        public SkillCatalogSnapshot snapshot() {
            return new SkillCatalogSnapshot(List.of(definition.summary()), List.of());
        }

        @Override
        public Optional<SkillDefinition> loadForExecution(String name) {
            return Optional.of(definition);
        }

        @Override
        public List<SkillDiagnostic> diagnostics() {
            return List.of();
        }
    }
}
