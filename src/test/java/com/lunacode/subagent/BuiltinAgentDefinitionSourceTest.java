package com.lunacode.subagent;

import com.lunacode.config.AgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinAgentDefinitionSourceTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsDefaultBuiltinAgentsWithoutVerification() {
        DefaultAgentDefinitionCatalog catalog = catalog(new BuiltinAgentDefinitionSource(agentConfig(false)));

        assertTrue(catalog.find("Explore").isPresent());
        assertTrue(catalog.find("Plan").isPresent());
        assertTrue(catalog.find("general-purpose").isPresent());
        assertFalse(catalog.find("Verification").isPresent());
        assertTrue(catalog.diagnostics().isEmpty());
    }

    @Test
    void verificationAgentIsConfigGatedAndBackgroundByDefault() {
        DefaultAgentDefinitionCatalog catalog = catalog(new BuiltinAgentDefinitionSource(agentConfig(true)));

        AgentDefinition verification = catalog.find("Verification").orElseThrow();
        assertTrue(verification.background());
        assertEquals("inherit", verification.model());
        assertTrue(verification.disallowedTools().contains("Agent"));
    }

    private DefaultAgentDefinitionCatalog catalog(AgentDefinitionSource source) {
        return new DefaultAgentDefinitionCatalog(
                List.of(source),
                new FrontmatterAgentDefinitionParser(),
                tempDir,
                tempDir,
                () -> Set.of("ReadFile", "WriteFile", "EditFile", "Bash", "Grep", "Glob", "Agent"),
                agentConfig(true)
        );
    }

    private AgentConfig agentConfig(boolean enableVerification) {
        return new AgentConfig(
                8,
                3,
                Path.of(".lunacode/plan.md"),
                120_000L,
                Map.of("haiku", "haiku-model"),
                enableVerification
        );
    }
}