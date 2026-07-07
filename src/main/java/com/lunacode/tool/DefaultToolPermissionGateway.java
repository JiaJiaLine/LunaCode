package com.lunacode.tool;

import com.lunacode.config.SandboxConfig;
import com.lunacode.permission.BashPathScanner;
import com.lunacode.permission.DangerousCommandBlacklist;
import com.lunacode.permission.DefaultPathSandbox;
import com.lunacode.permission.DefaultPermissionEngine;
import com.lunacode.permission.PathSandbox;
import com.lunacode.permission.PermissionEvaluation;
import com.lunacode.permission.PermissionEvaluationRequest;
import com.lunacode.permission.PermissionModePolicy;
import com.lunacode.permission.PermissionRuleMatcher;
import com.lunacode.permission.PermissionRuleStore;
import com.lunacode.permission.PermissionTargetExtractor;
import com.lunacode.permission.SensitivePathPolicy;
import com.lunacode.permission.YamlPermissionRuleStore;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;

import java.nio.file.Path;
import java.util.Objects;

public final class DefaultToolPermissionGateway implements ToolPermissionGateway {
    private final Path workspaceRoot;
    private final DefaultPermissionEngine permissionEngine;

    public DefaultToolPermissionGateway(Path workspaceRoot) {
        this(workspaceRoot, defaultEngine(workspaceRoot));
    }

    public DefaultToolPermissionGateway(Path workspaceRoot, DefaultPermissionEngine permissionEngine) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        this.permissionEngine = Objects.requireNonNull(permissionEngine, "permissionEngine");
    }

    @Override
    public PermissionEvaluation evaluate(ToolUse toolUse, Tool tool, AgentRunConfig config) {
        return permissionEngine.evaluate(new PermissionEvaluationRequest(
                toolUse,
                tool,
                config.mode(),
                config.permissionMode(),
                config.planFile(),
                config.workDir()
        ));
    }

    @Override
    public PermissionDecision decide(ToolUse toolUse, Tool tool, AgentMode mode, Path planFile) {
        AgentRunConfig config = new AgentRunConfig(workspaceRoot, mode, planFile, 8, 3, java.time.Clock.systemDefaultZone());
        return toLegacyDecision(evaluate(toolUse, tool, config).decision());
    }

    private PermissionDecision toLegacyDecision(PermissionEvaluation.Decision decision) {
        return switch (decision) {
            case ALLOW -> PermissionDecision.ALLOW;
            case ASK -> PermissionDecision.ASK;
            case DENY -> PermissionDecision.DENY;
        };
    }

    private static DefaultPermissionEngine defaultEngine(Path workspaceRoot) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        PathSandbox pathSandbox = new DefaultPathSandbox(root, SandboxConfig.defaults());
        PermissionRuleStore store = new YamlPermissionRuleStore(root);
        SensitivePathPolicy sensitivePathPolicy = new SensitivePathPolicy();
        return new DefaultPermissionEngine(
                store,
                new PermissionTargetExtractor(pathSandbox, new BashPathScanner(), sensitivePathPolicy),
                new PermissionRuleMatcher(),
                new PermissionModePolicy(),
                new DangerousCommandBlacklist()
        );
    }

}
