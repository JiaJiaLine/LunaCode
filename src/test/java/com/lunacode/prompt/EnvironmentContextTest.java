package com.lunacode.prompt;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentContextTest {
    @Test
    void rendersWorktreeSummaryForManagedWorktreePath() {
        EnvironmentContext context = new EnvironmentContext(
                Path.of("repo/.lunacode/worktrees/agent-a1234567"),
                "Windows",
                Instant.parse("2026-07-07T00:00:00Z"),
                GitStatusSnapshot.unknown("unknown")
        );

        String rendered = context.render();

        assertTrue(rendered.contains("Worktree: agent-a1234567"));
        assertTrue(rendered.contains("隔离目录"));
    }

    @Test
    void omitsWorktreeSummaryForNormalPath() {
        EnvironmentContext context = new EnvironmentContext(
                Path.of("repo"),
                "Windows",
                Instant.parse("2026-07-07T00:00:00Z"),
                GitStatusSnapshot.unknown("unknown")
        );

        assertFalse(context.render().contains("Worktree:"));
    }
}
