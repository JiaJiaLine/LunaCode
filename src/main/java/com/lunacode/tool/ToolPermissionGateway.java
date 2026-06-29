package com.lunacode.tool;

import com.lunacode.permission.PermissionDecisionLayer;
import com.lunacode.permission.PermissionEvaluation;
import com.lunacode.permission.PermissionEvaluationRequest;
import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;

import java.nio.file.Path;
import java.util.List;

public interface ToolPermissionGateway {
    PermissionDecision decide(ToolUse toolUse, Tool tool, AgentMode mode, Path planFile);

    default PermissionEvaluation evaluate(ToolUse toolUse, Tool tool, AgentRunConfig config) {
        PermissionDecision decision = decide(toolUse, tool, config.mode(), config.planFile());
        PermissionMode mode = config.permissionMode();
        return switch (decision) {
            case ALLOW -> PermissionEvaluation.allow(PermissionDecisionLayer.MODE_POLICY, "权限模式自动允许", List.of(), List.of());
            case ASK -> PermissionEvaluation.ask(PermissionDecisionLayer.MODE_POLICY, "权限模式需要确认", toolUse.name() + "(" + (toolUse.input() == null ? "{}" : toolUse.input()) + ")", List.of());
            case DENY -> PermissionEvaluation.deny(PermissionDecisionLayer.MODE_POLICY, "权限模式拒绝工具调用", List.of(), List.of());
        };
    }
}
