package com.lunacode.subagent;

import com.lunacode.skill.ToolAccessPolicy;

import java.nio.file.Path;
import java.util.Objects;

import com.lunacode.conversation.ConversationManager;
import com.lunacode.runtime.AgentRunConfig;

public record SubAgentParentContext(
        ConversationManager parentConversation,
        AgentRunConfig parentConfig,
        ToolAccessPolicy parentToolPolicy,
        boolean parentIsBackground,
        boolean parentIsFork,
        String sessionId,
        Path workspaceRoot
) {
    public SubAgentParentContext {
        parentConfig = Objects.requireNonNull(parentConfig, "parentConfig");
        parentToolPolicy = parentToolPolicy == null ? parentConfig.toolAccessPolicy() : parentToolPolicy;
        parentIsBackground = parentIsBackground || parentConfig.backgroundAgent();
        parentIsFork = parentIsFork || parentConfig.forkAgent();
        sessionId = sessionId == null ? "" : sessionId;
        workspaceRoot = (workspaceRoot == null ? parentConfig.workDir() : workspaceRoot).toAbsolutePath().normalize();
    }
}
