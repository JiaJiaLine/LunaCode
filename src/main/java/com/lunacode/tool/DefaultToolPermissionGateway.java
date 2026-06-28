package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.agent.AgentMode;

import java.nio.file.Path;
import java.util.Objects;

public final class DefaultToolPermissionGateway implements ToolPermissionGateway {
    private final Path workspaceRoot;

    public DefaultToolPermissionGateway(Path workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
    }

    @Override
    public PermissionDecision decide(ToolUse toolUse, Tool tool, AgentMode mode, Path planFile) {
        if (tool == null) {
            return PermissionDecision.DENY;
        }
        if ("AskUserQuestion".equals(tool.name())) {
            return mode == AgentMode.PLAN ? PermissionDecision.ALLOW : PermissionDecision.DENY;
        }
        if (mode == AgentMode.PLAN && isPlanFileWrite(toolUse, tool, planFile)) {
            return PermissionDecision.ALLOW;
        }
        if (tool.isReadOnly() && !tool.isDestructive()) {
            return PermissionDecision.ALLOW;
        }
        if ("Bash".equals(tool.name()) || tool.isDestructive()) {
            return PermissionDecision.ASK;
        }
        return PermissionDecision.ALLOW;
    }

    private boolean isPlanFileWrite(ToolUse toolUse, Tool tool, Path planFile) {
        if (!"WriteFile".equals(tool.name()) && !"EditFile".equals(tool.name())) {
            return false;
        }
        Path target = targetPath(toolUse.input());
        if (target == null || planFile == null) {
            return false;
        }
        Path normalizedTarget = target.isAbsolute() ? target.toAbsolutePath().normalize() : workspaceRoot.resolve(target).normalize();
        Path normalizedPlan = planFile.isAbsolute() ? planFile.toAbsolutePath().normalize() : workspaceRoot.resolve(planFile).normalize();
        return normalizedTarget.equals(normalizedPlan);
    }

    private Path targetPath(JsonNode input) {
        if (input == null) {
            return null;
        }
        for (String name : new String[]{"path", "file_path"}) {
            if (input.hasNonNull(name) && !input.path(name).asText().isBlank()) {
                return Path.of(input.path(name).asText());
            }
        }
        return null;
    }
}
