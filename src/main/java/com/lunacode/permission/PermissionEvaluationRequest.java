package com.lunacode.permission;

import com.lunacode.runtime.AgentMode;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolUse;

import java.nio.file.Path;
import java.util.Objects;

public record PermissionEvaluationRequest(
        ToolUse toolUse,
        Tool tool,
        AgentMode agentMode,
        PermissionMode permissionMode,
        Path planFile,
        Path workDir
) {
    public PermissionEvaluationRequest(ToolUse toolUse, Tool tool, AgentMode agentMode, PermissionMode permissionMode, Path planFile) {
        this(toolUse, tool, agentMode, permissionMode, planFile, null);
    }

    public PermissionEvaluationRequest {
        toolUse = Objects.requireNonNull(toolUse, "toolUse");
        agentMode = agentMode == null ? AgentMode.DEFAULT : agentMode;
        permissionMode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
        workDir = workDir == null ? null : workDir.toAbsolutePath().normalize();
    }
}
