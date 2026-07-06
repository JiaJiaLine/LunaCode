package com.lunacode.subagent;

import com.lunacode.config.AgentConfig;
import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubAgentModelResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void inheritUsesParentModelOverride() {
        AgentRunConfig parent = parentConfig().withModelOverride(Optional.of("parent-model"));
        Optional<String> resolved = new SubAgentModelResolver(AgentConfig.defaults())
                .resolve(definition("inherit"), parent);

        assertEquals(Optional.of("parent-model"), resolved);
    }

    @Test
    void aliasesResolveFromAgentConfig() {
        AgentConfig config = new AgentConfig(8, 3, Path.of("plan.md"), 120_000L, Map.of("sonnet", "mapped-sonnet"));

        Optional<String> resolved = new SubAgentModelResolver(config).resolve(definition("sonnet"), parentConfig());

        assertEquals(Optional.of("mapped-sonnet"), resolved);
    }

    @Test
    void missingAliasFailsFast() {
        SubAgentModelResolver resolver = new SubAgentModelResolver(AgentConfig.defaults());

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(definition("opus"), parentConfig()));
    }

    @Test
    void fullModelNamePassesThrough() {
        Optional<String> resolved = new SubAgentModelResolver(AgentConfig.defaults())
                .resolve(definition("custom-model-v1"), parentConfig());

        assertEquals(Optional.of("custom-model-v1"), resolved);
    }

    private AgentRunConfig parentConfig() {
        return new AgentRunConfig(tempDir, AgentMode.DEFAULT, tempDir.resolve("plan.md"), 8, 3, Clock.systemUTC());
    }

    private AgentDefinition definition(String model) {
        return new AgentDefinition(
                "reviewer",
                "review code",
                List.of(),
                List.of(),
                model,
                OptionalInt.empty(),
                Optional.empty(),
                "system prompt",
                tempDir.resolve("reviewer.md"),
                AgentDefinitionSourceKind.PROJECT
        );
    }
}