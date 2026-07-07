package com.lunacode.runtime;

import com.lunacode.permission.PermissionMode;
import com.lunacode.skill.SkillPromptContext;
import com.lunacode.skill.ToolAccessPolicy;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public record AgentRunConfig(
        Path workDir,
        AgentMode mode,
        PermissionMode permissionMode,
        Path planFile,
        int maxIterations,
        int maxConsecutiveUnknownTools,
        Clock clock,
        ToolAccessPolicy toolAccessPolicy,
        Optional<String> modelOverride,
        SkillPromptContext skillPromptContext,
        Optional<String> subAgentSystemPrompt,
        boolean backgroundAgent,
        boolean forkAgent
) {
    public AgentRunConfig(Path workDir, AgentMode mode, Path planFile, int maxIterations, int maxConsecutiveUnknownTools, Clock clock) {
        this(workDir, mode, mode == AgentMode.PLAN ? PermissionMode.PLAN : PermissionMode.DEFAULT, planFile, maxIterations, maxConsecutiveUnknownTools, clock);
    }

    public AgentRunConfig(Path workDir, AgentMode mode, PermissionMode permissionMode, Path planFile, int maxIterations, int maxConsecutiveUnknownTools, Clock clock) {
        this(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, null, Optional.empty(), SkillPromptContext.empty(), Optional.empty(), false, false);
    }

    public AgentRunConfig(
            Path workDir,
            AgentMode mode,
            PermissionMode permissionMode,
            Path planFile,
            int maxIterations,
            int maxConsecutiveUnknownTools,
            Clock clock,
            ToolAccessPolicy toolAccessPolicy,
            Optional<String> modelOverride,
            SkillPromptContext skillPromptContext
    ) {
        this(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, modelOverride, skillPromptContext, Optional.empty(), false, false);
    }

    public AgentRunConfig {
        workDir = Objects.requireNonNull(workDir, "workDir").toAbsolutePath().normalize();
        mode = mode == null ? AgentMode.DEFAULT : mode;
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
        planFile = planFile == null ? workDir.resolve(".lunacode/plan.md") : normalizePlanFile(workDir, planFile);
        maxIterations = maxIterations <= 0 ? 8 : maxIterations;
        maxConsecutiveUnknownTools = maxConsecutiveUnknownTools <= 0 ? 3 : maxConsecutiveUnknownTools;
        clock = clock == null ? Clock.systemDefaultZone() : clock;
        modelOverride = modelOverride == null ? Optional.empty() : modelOverride.map(String::strip).filter(value -> !value.isBlank());
        skillPromptContext = skillPromptContext == null ? SkillPromptContext.empty() : skillPromptContext;
        subAgentSystemPrompt = subAgentSystemPrompt == null ? Optional.empty() : subAgentSystemPrompt.map(String::strip).filter(value -> !value.isBlank());
    }

    public AgentRunConfig withWorkDir(Path newWorkDir) {
        Path normalizedWorkDir = Objects.requireNonNull(newWorkDir, "newWorkDir").toAbsolutePath().normalize();
        return new AgentRunConfig(normalizedWorkDir, mode, permissionMode, normalizedWorkDir.resolve(".lunacode/plan.md"), maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, modelOverride, skillPromptContext, subAgentSystemPrompt, backgroundAgent, forkAgent);
    }
    public AgentRunConfig withToolAccessPolicy(ToolAccessPolicy policy) {
        return new AgentRunConfig(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, policy, modelOverride, skillPromptContext, subAgentSystemPrompt, backgroundAgent, forkAgent);
    }

    public AgentRunConfig withModelOverride(Optional<String> model) {
        return new AgentRunConfig(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, model, skillPromptContext, subAgentSystemPrompt, backgroundAgent, forkAgent);
    }

    public AgentRunConfig withSkillPromptContext(SkillPromptContext context) {
        return new AgentRunConfig(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, modelOverride, context, subAgentSystemPrompt, backgroundAgent, forkAgent);
    }

    public AgentRunConfig withSubAgentSystemPrompt(String prompt) {
        return new AgentRunConfig(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, modelOverride, skillPromptContext, Optional.ofNullable(prompt), backgroundAgent, forkAgent);
    }

    public AgentRunConfig asSubAgent(boolean backgroundAgent, boolean forkAgent) {
        return new AgentRunConfig(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, modelOverride, skillPromptContext, subAgentSystemPrompt, backgroundAgent, forkAgent);
    }

    public AgentRunConfig withMaxIterations(int maxIterations) {
        return new AgentRunConfig(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, modelOverride, skillPromptContext, subAgentSystemPrompt, backgroundAgent, forkAgent);
    }

    public AgentRunConfig withPermissionMode(PermissionMode permissionMode) {
        return new AgentRunConfig(workDir, mode, permissionMode, planFile, maxIterations, maxConsecutiveUnknownTools, clock, toolAccessPolicy, modelOverride, skillPromptContext, subAgentSystemPrompt, backgroundAgent, forkAgent);
    }

    private static Path normalizePlanFile(Path workDir, Path planFile) {
        Path path = planFile.isAbsolute() ? planFile : workDir.resolve(planFile);
        return path.toAbsolutePath().normalize();
    }
}
