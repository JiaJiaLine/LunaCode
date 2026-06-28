package com.lunacode.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptBuilderTest {
    @Test
    void defaultPromptContainsRoleAndEnvironment() {
        String prompt = new SystemPromptBuilder().build(new SystemPromptConfig(Path.of("/work"), "TestOS", Instant.parse("2026-06-27T00:00:00Z"), AgentMode.DEFAULT, Path.of("/work/plan.md")));

        assertTrue(prompt.contains("LunaCode"));
        assertTrue(prompt.contains("/work"));
        assertTrue(prompt.contains("TestOS"));
        assertTrue(prompt.contains("2026-06-27T00:00:00Z"));
        assertFalse(prompt.contains("Plan mode is active"));
    }

    @Test
    void planPromptContainsPlanModeInstructions() {
        String prompt = new SystemPromptBuilder().build(new SystemPromptConfig(Path.of("/work"), "TestOS", Instant.parse("2026-06-27T00:00:00Z"), AgentMode.PLAN, Path.of("/work/.lunacode/plan.md")));

        assertTrue(prompt.contains("Plan mode is active"));
        assertTrue(prompt.contains("AskUserQuestion"));
        assertTrue(prompt.contains("/work/.lunacode/plan.md"));
    }
}