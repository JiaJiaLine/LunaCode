package com.lunacode.config;

import java.nio.file.Path;
import java.util.Map;

public record AgentConfig(
        int maxIterations,
        int maxConsecutiveUnknownTools,
        Path planFile,
        long autoBackgroundMs,
        Map<String, String> modelAliases,
        boolean enableVerificationAgent
) {
    public AgentConfig(int maxIterations, int maxConsecutiveUnknownTools, Path planFile) {
        this(maxIterations, maxConsecutiveUnknownTools, planFile, 120_000L, Map.of(), false);
    }

    public AgentConfig(int maxIterations, int maxConsecutiveUnknownTools, Path planFile, long autoBackgroundMs, Map<String, String> modelAliases) {
        this(maxIterations, maxConsecutiveUnknownTools, planFile, autoBackgroundMs, modelAliases, false);
    }

    public static AgentConfig defaults() {
        return new AgentConfig(8, 3, Path.of(".lunacode/plan.md"), 120_000L, Map.of(), false);
    }

    public AgentConfig {
        maxIterations = maxIterations <= 0 ? 20 : maxIterations;
        maxConsecutiveUnknownTools = maxConsecutiveUnknownTools <= 0 ? 3 : maxConsecutiveUnknownTools;
        planFile = planFile == null ? Path.of(".lunacode/plan.md") : planFile;
        autoBackgroundMs = autoBackgroundMs <= 0 ? 120_000L : autoBackgroundMs;
        modelAliases = modelAliases == null ? Map.of() : Map.copyOf(modelAliases);
    }

    public long getAutoBackgroundMs() {
        return autoBackgroundMs;
    }
}