package com.lunacode.subagent;

import com.lunacode.config.AgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAgentDefinitionCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void higherPriorityDefinitionOverridesLowerPriority() {
        AgentDefinitionSource source = (projectRoot, userHome) -> List.of(
                AgentDefinitionCandidate.memory(AgentDefinitionSourceKind.PLUGIN, "plugin.md", definition("reviewer", "plugin")),
                AgentDefinitionCandidate.memory(AgentDefinitionSourceKind.BUILTIN, "builtin.md", definition("reviewer", "builtin")),
                AgentDefinitionCandidate.memory(AgentDefinitionSourceKind.USER, "user.md", definition("reviewer", "user")),
                AgentDefinitionCandidate.memory(AgentDefinitionSourceKind.PROJECT, "project.md", definition("reviewer", "project"))
        );

        DefaultAgentDefinitionCatalog catalog = new DefaultAgentDefinitionCatalog(
                List.of(source),
                new FrontmatterAgentDefinitionParser(),
                tempDir,
                tempDir,
                () -> Set.of("ReadFile", "Grep", "Bash", "Agent"),
                AgentConfig.defaults()
        );

        AgentDefinition definition = catalog.find("reviewer").orElseThrow();
        assertEquals(AgentDefinitionSourceKind.PROJECT, definition.source());
        assertTrue(definition.systemPrompt().contains("project"));
    }

    @Test
    void badDefinitionProducesWarningAndDoesNotBlockOthers() {
        AgentDefinitionSource source = (projectRoot, userHome) -> List.of(
                AgentDefinitionCandidate.memory(AgentDefinitionSourceKind.USER, "bad.md", "---\ndescription: bad\n---\nbody"),
                AgentDefinitionCandidate.memory(AgentDefinitionSourceKind.PROJECT, "good.md", definition("good", "ok"))
        );

        DefaultAgentDefinitionCatalog catalog = new DefaultAgentDefinitionCatalog(
                List.of(source),
                new FrontmatterAgentDefinitionParser(),
                tempDir,
                tempDir,
                () -> Set.of("ReadFile", "Grep", "Bash", "Agent"),
                AgentConfig.defaults()
        );

        assertTrue(catalog.find("good").isPresent());
        assertFalse(catalog.diagnostics().isEmpty());
    }

    private String definition(String name, String body) {
        return """
                ---
                name: %s
                description: desc
                tools:
                  - ReadFile
                ---
                %s
                """.formatted(name, body);
    }
}