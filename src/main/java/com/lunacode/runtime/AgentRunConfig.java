package com.lunacode.runtime;

import com.lunacode.permission.PermissionMode;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

public record AgentRunConfig(
        Path workDir,
        AgentMode mode,
        PermissionMode permissionMode,
        Path planFile,
        int maxIterations,
        int maxConsecutiveUnknownTools,
        Clock clock
) {
    public AgentRunConfig(Path workDir, AgentMode mode, Path planFile, int maxIterations, int maxConsecutiveUnknownTools, Clock clock) {
        this(workDir, mode, mode == AgentMode.PLAN ? PermissionMode.PLAN : PermissionMode.DEFAULT, planFile, maxIterations, maxConsecutiveUnknownTools, clock);
    }

    public AgentRunConfig {
        workDir = Objects.requireNonNull(workDir, "workDir").toAbsolutePath().normalize();
        mode = mode == null ? AgentMode.DEFAULT : mode;
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
        planFile = planFile == null ? workDir.resolve(".lunacode/plan.md") : normalizePlanFile(workDir, planFile);
        maxIterations = maxIterations <= 0 ? 8 : maxIterations;
        maxConsecutiveUnknownTools = maxConsecutiveUnknownTools <= 0 ? 3 : maxConsecutiveUnknownTools;
        clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    private static Path normalizePlanFile(Path workDir, Path planFile) {
        Path path = planFile.isAbsolute() ? planFile : workDir.resolve(planFile);
        return path.toAbsolutePath().normalize();
    }
}
