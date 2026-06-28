package com.lunacode.agent;

import java.nio.file.Path;
import java.time.Instant;

public record SystemPromptConfig(
        Path workDir,
        String osName,
        Instant now,
        AgentMode mode,
        Path planFile
) {}
