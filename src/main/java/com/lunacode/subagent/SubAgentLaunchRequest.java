package com.lunacode.subagent;

import com.lunacode.worktree.WorktreeRecord;

import java.util.Optional;

public record SubAgentLaunchRequest(
        SubAgentKind kind,
        Optional<AgentDefinition> definition,
        String task,
        boolean requestedBackground,
        SubAgentParentContext parentContext,
        SubAgentNotificationPolicy notificationPolicy,
        Optional<WorktreeRecord> worktree
) {
    public SubAgentLaunchRequest(
            SubAgentKind kind,
            Optional<AgentDefinition> definition,
            String task,
            boolean requestedBackground,
            SubAgentParentContext parentContext,
            SubAgentNotificationPolicy notificationPolicy
    ) {
        this(kind, definition, task, requestedBackground, parentContext, notificationPolicy, Optional.empty());
    }
    public SubAgentLaunchRequest {
        kind = kind == null ? SubAgentKind.DEFINED : kind;
        definition = definition == null ? Optional.empty() : definition;
        task = task == null ? "" : task.strip();
        notificationPolicy = notificationPolicy == null ? SubAgentNotificationPolicy.TOOL : notificationPolicy;
        worktree = worktree == null ? Optional.empty() : worktree;
    }
}
