package com.lunacode.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SystemChannelTest {
    @Test
    void keepsStaticPromptAndEnvironmentSeparated() {
        StaticSystemPrompt staticPrompt = new StaticSystemPromptBuilder().build();
        EnvironmentContext environment = new EnvironmentContext(Path.of("."), "TestOS", Instant.parse("2026-06-28T00:00:00Z"), new GitStatusSnapshot(true, "main", true, "M file"));
        SystemChannel channel = new SystemChannel(staticPrompt, environment);

        assertSame(staticPrompt, channel.staticPrompt());
        assertSame(environment, channel.environmentContext());
        assertFalse(channel.staticPrompt().render().contains("TestOS"));
        assertTrue(channel.environmentContext().render().contains("TestOS"));
        assertTrue(channel.environmentContext().render().contains("M file"));
    }

    @Test
    void gitFailureFallsBackToUnknownEnvironment() {
        AgentRunConfig config = new AgentRunConfig(Path.of("missing-dir-for-git-status"), AgentMode.DEFAULT, Path.of("plan.md"), 1, 1, null);

        EnvironmentContext environment = new EnvironmentContextCollector().collect(config);

        assertNotNull(environment.gitStatus());
        assertEquals("unknown", environment.gitStatus().branch());
    }
}
