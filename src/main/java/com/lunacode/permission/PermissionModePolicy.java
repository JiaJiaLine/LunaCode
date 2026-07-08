package com.lunacode.permission;

import com.lunacode.runtime.AgentMode;
import com.lunacode.tool.Tool;

public final class PermissionModePolicy {
    public PermissionEvaluation.Decision decide(PermissionMode permissionMode, AgentMode agentMode, Tool tool) {
        if (tool == null) {
            return PermissionEvaluation.Decision.DENY;
        }
        if ("AskUserQuestion".equals(tool.name())) {
            return agentMode == AgentMode.PLAN ? PermissionEvaluation.Decision.ALLOW : PermissionEvaluation.Decision.DENY;
        }
        if ("team".equals(tool.category()) && !tool.isDestructive()) {
            return PermissionEvaluation.Decision.ALLOW;
        }
        PermissionMode mode = permissionMode == null ? PermissionMode.DEFAULT : permissionMode;
        return switch (mode) {
            case DEFAULT, PLAN -> defaultDecision(tool);
            case ACCEPT_EDITS -> acceptEditsDecision(tool);
            case BYPASS_PERMISSIONS -> PermissionEvaluation.Decision.ALLOW;
        };
    }

    private PermissionEvaluation.Decision defaultDecision(Tool tool) {
        if (isReadOnlySearchOrFile(tool)) {
            return PermissionEvaluation.Decision.ALLOW;
        }
        if ("Bash".equals(tool.name()) || tool.isDestructive()) {
            return PermissionEvaluation.Decision.ASK;
        }
        return tool.isReadOnly() ? PermissionEvaluation.Decision.ALLOW : PermissionEvaluation.Decision.ASK;
    }

    private PermissionEvaluation.Decision acceptEditsDecision(Tool tool) {
        if ("WriteFile".equals(tool.name()) || "EditFile".equals(tool.name())) {
            return PermissionEvaluation.Decision.ALLOW;
        }
        return defaultDecision(tool);
    }

    private boolean isReadOnlySearchOrFile(Tool tool) {
        return ("ReadFile".equals(tool.name()) || "Glob".equals(tool.name()) || "Grep".equals(tool.name()))
                && tool.isReadOnly()
                && !tool.isDestructive();
    }
}
