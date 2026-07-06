package com.lunacode.subagent;

import java.util.Optional;

public record SubAgentLaunchRequest(
        SubAgentKind kind,
        Optional<AgentDefinition> definition,
        String task,
        boolean requestedBackground,
        SubAgentParentContext parentContext,
        SubAgentNotificationPolicy notificationPolicy
) {
    public SubAgentLaunchRequest {
        kind = kind == null ? SubAgentKind.DEFINED : kind;
        definition = definition == null ? Optional.empty() : definition;
        task = task == null ? "" : task.strip();
        notificationPolicy = notificationPolicy == null ? SubAgentNotificationPolicy.TOOL : notificationPolicy;
    }
}
