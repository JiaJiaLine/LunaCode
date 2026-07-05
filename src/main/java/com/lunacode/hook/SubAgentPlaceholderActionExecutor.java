package com.lunacode.hook;

import java.util.Map;

public final class SubAgentPlaceholderActionExecutor implements HookActionExecutor {
    @Override
    public HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        if (!(hook.action() instanceof HookAction.SubAgent subAgent)) {
            return HookActionResult.failure("Hook action 不是 sub_agent", Map.of("errorType", "invalid_action"));
        }
        return HookActionResult.success(
                "sub_agent 动作尚未实现，已跳过: " + subAgent.name(),
                Map.of("implemented", false, "subAgent", subAgent.name())
        );
    }
}
