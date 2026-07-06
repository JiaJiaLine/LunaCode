package com.lunacode.subagent;

import com.lunacode.skill.ToolAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPolicyResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void whitelistIsNarrowedThenBlacklistWins() {
        AgentDefinition definition = definition(List.of("Read", "Grep", "Bash"), List.of("Bash"));

        ToolAccessPolicy policy = new ToolPolicyResolver(null).resolve(null, definition, false, false);

        assertTrue(policy.allows("ReadFile"));
        assertTrue(policy.allows("Grep"));
        assertFalse(policy.allows("Bash"));
        assertFalse(policy.allows("WriteFile"));
    }

    @Test
    void disallowedToolsRemoveInheritedTools() {
        ToolAccessPolicy parent = ToolAccessPolicy.restricted(Set.of("ReadFile", "Bash"), Set.of());
        AgentDefinition definition = definition(List.of(), List.of("Bash"));

        ToolAccessPolicy policy = new ToolPolicyResolver(null).resolve(parent, definition, false, false);

        assertTrue(policy.allows("ReadFile"));
        assertFalse(policy.allows("Bash"));
    }

    @Test
    void backgroundAndForkCannotUseAgentEvenWhenAllowed() {
        ToolAccessPolicy parent = ToolAccessPolicy.restricted(Set.of("Agent", "ReadFile"), Set.of());
        AgentDefinition definition = definition(List.of("Agent", "Read"), List.of());

        ToolAccessPolicy backgroundPolicy = new ToolPolicyResolver(null).resolve(parent, definition, true, false);
        ToolAccessPolicy forkPolicy = new ToolPolicyResolver(null).resolve(parent, definition, false, true);

        assertFalse(backgroundPolicy.allows("Agent"));
        assertFalse(forkPolicy.allows("Agent"));
        assertTrue(backgroundPolicy.allows("ReadFile"));
        assertTrue(forkPolicy.allows("ReadFile"));
    }

    private AgentDefinition definition(List<String> tools, List<String> disallowedTools) {
        return new AgentDefinition(
                "reviewer",
                "review code",
                tools,
                disallowedTools,
                "inherit",
                OptionalInt.empty(),
                Optional.empty(),
                "system prompt",
                tempDir.resolve("reviewer.md"),
                AgentDefinitionSourceKind.PROJECT
        );
    }
}