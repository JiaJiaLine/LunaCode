package com.lunacode.tool;

import com.lunacode.agent.AgentMode;

import java.nio.file.Path;

public interface ToolPermissionGateway {
    PermissionDecision decide(ToolUse toolUse, Tool tool, AgentMode mode, Path planFile);
}
