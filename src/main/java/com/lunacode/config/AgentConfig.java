package com.lunacode.config;

import java.nio.file.Path;

public record AgentConfig(
        int maxIterations,
        int maxConsecutiveUnknownTools,
        Path planFile
) {
    public static AgentConfig defaults() {
        return new AgentConfig(8, 3, Path.of(".lunacode/plan.md"));
    }

    public AgentConfig {
        maxIterations = maxIterations <= 0 ? 20 : maxIterations;
        maxConsecutiveUnknownTools = maxConsecutiveUnknownTools <= 0 ? 3 : maxConsecutiveUnknownTools;
        planFile = planFile == null ? Path.of(".lunacode/plan.md") : planFile;
    }
}
