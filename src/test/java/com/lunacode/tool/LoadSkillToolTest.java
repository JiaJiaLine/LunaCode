package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.skill.DefaultSkillInvocationPlanner;
import com.lunacode.skill.SkillCatalog;
import com.lunacode.skill.SkillCatalogSnapshot;
import com.lunacode.skill.SkillContextPolicy;
import com.lunacode.skill.SkillDefinition;
import com.lunacode.skill.SkillDiagnostic;
import com.lunacode.skill.SkillExecutionMode;
import com.lunacode.skill.SkillOrigin;
import com.lunacode.skill.SkillSourceKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadSkillToolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void loadsSkillAndReplacesArguments() {
        LoadSkillTool tool = new LoadSkillTool(new DefaultSkillInvocationPlanner(new FixedCatalog(
                definition("review $ARGUMENTS", Optional.of(Path.of("examples")))
        )));
        ObjectNode input = MAPPER.createObjectNode()
                .put("name", "commit")
                .put("arguments", "security");

        ToolResult result = tool.execute(null, input);

        assertFalse(result.isError());
        assertTrue(result.content().contains("review security"));
        assertTrue(result.content().contains("Resource root:"));
        assertEquals(Boolean.TRUE, result.metadata().get("loadedSkill"));
        assertEquals("commit", result.metadata().get("skillName"));
    }

    @Test
    void unknownSkillReturnsControlledError() {
        LoadSkillTool tool = new LoadSkillTool(new DefaultSkillInvocationPlanner(new EmptyCatalog()));
        ObjectNode input = MAPPER.createObjectNode().put("name", "missing");

        ToolResult result = tool.execute(null, input);

        assertTrue(result.isError());
        assertEquals("skill_load_failed", result.metadata().get("errorType"));
    }

    private SkillDefinition definition(String body, Optional<Path> resourceRoot) {
        return new SkillDefinition(
                "commit",
                "commit helper",
                SkillExecutionMode.INLINE,
                SkillContextPolicy.FULL,
                Optional.empty(),
                List.of(),
                body,
                new SkillOrigin(SkillSourceKind.PROJECT, "test", Optional.empty(), 300),
                resourceRoot
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

    private static final class EmptyCatalog implements SkillCatalog {
        @Override
        public SkillCatalogSnapshot snapshot() {
            return new SkillCatalogSnapshot(List.of(), List.of());
        }

        @Override
        public Optional<SkillDefinition> loadForExecution(String name) {
            return Optional.empty();
        }

        @Override
        public List<SkillDiagnostic> diagnostics() {
            return List.of();
        }
    }
}
