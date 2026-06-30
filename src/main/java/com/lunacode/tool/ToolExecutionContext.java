package com.lunacode.tool;

import com.lunacode.config.SandboxConfig;
import com.lunacode.interaction.UserQuestionBroker;
import com.lunacode.permission.SandboxRoot;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record ToolExecutionContext(
        Path workspaceRoot,
        Duration commandTimeout,
        int maxContentChars,
        SensitiveValueMasker masker,
        UserQuestionBroker userQuestionBroker,
        CommandSandbox commandSandbox,
        SandboxConfig sandboxConfig,
        List<SandboxRoot> sandboxRoots
) {
    public ToolExecutionContext(Path workspaceRoot, Duration commandTimeout, int maxContentChars, SensitiveValueMasker masker) {
        this(workspaceRoot, commandTimeout, maxContentChars, masker, null);
    }

    public ToolExecutionContext(Path workspaceRoot, Duration commandTimeout, int maxContentChars, SensitiveValueMasker masker, UserQuestionBroker userQuestionBroker) {
        this(workspaceRoot, commandTimeout, maxContentChars, masker, userQuestionBroker, new DirectCommandSandbox(), SandboxConfig.defaults(), List.of());
    }

    public ToolExecutionContext {
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        commandTimeout = commandTimeout == null ? Duration.ofSeconds(30) : commandTimeout;
        maxContentChars = maxContentChars <= 0 ? 20_000 : maxContentChars;
        masker = masker == null ? new SensitiveValueMasker() : masker;
        commandSandbox = commandSandbox == null ? new DirectCommandSandbox() : commandSandbox;
        sandboxConfig = sandboxConfig == null ? SandboxConfig.defaults() : sandboxConfig;
        sandboxRoots = sandboxRoots == null ? List.of() : List.copyOf(sandboxRoots);
    }
}
