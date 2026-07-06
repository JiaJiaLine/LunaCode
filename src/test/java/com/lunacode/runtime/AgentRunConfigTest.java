package com.lunacode.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsAreNotSubAgentScope() {
        AgentRunConfig config = new AgentRunConfig(tempDir, AgentMode.DEFAULT, tempDir.resolve("plan.md"), 8, 3, Clock.systemUTC());

        assertFalse(config.backgroundAgent());
        assertFalse(config.forkAgent());
    }

    @Test
    void canMarkBackgroundForkScope() {
        AgentRunConfig config = new AgentRunConfig(tempDir, AgentMode.DEFAULT, tempDir.resolve("plan.md"), 8, 3, Clock.systemUTC())
                .asSubAgent(true, true);

        assertTrue(config.backgroundAgent());
        assertTrue(config.forkAgent());
    }
}