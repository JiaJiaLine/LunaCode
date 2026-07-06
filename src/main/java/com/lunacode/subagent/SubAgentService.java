package com.lunacode.subagent;

import com.lunacode.hook.HookExecutionScope;
import com.lunacode.tool.ToolResult;

public interface SubAgentService {
    ToolResult launchFromTool(AgentToolRequest request, SubAgentParentContext parentContext);

    String launchFromHook(String subagentType, String task, HookExecutionScope scope);

    SubAgentRunHandle startForeground(SubAgentLaunchRequest request);

    String launchBackground(SubAgentLaunchRequest request);
}
