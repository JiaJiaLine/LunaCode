package com.lunacode.prompt;

import com.lunacode.runtime.AgentMode;

import java.nio.file.Path;
import java.time.Instant;

public record SystemPromptConfig(
        Path workDir,
        String osName,
        Instant now,
        AgentMode mode,
        Path planFile
) {}
