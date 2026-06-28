package com.lunacode.tool;

import com.lunacode.agent.UserQuestionBroker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record ToolExecutionContext(
        Path workspaceRoot,
        Duration commandTimeout,
        int maxContentChars,
        SensitiveValueMasker masker,
        UserQuestionBroker userQuestionBroker
) {
    public ToolExecutionContext(Path workspaceRoot, Duration commandTimeout, int maxContentChars, SensitiveValueMasker masker) {
        this(workspaceRoot, commandTimeout, maxContentChars, masker, null);
    }

    public ToolExecutionContext {
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        commandTimeout = commandTimeout == null ? Duration.ofSeconds(30) : commandTimeout;
        maxContentChars = maxContentChars <= 0 ? 20_000 : maxContentChars;
        masker = masker == null ? new SensitiveValueMasker() : masker;
    }
}
