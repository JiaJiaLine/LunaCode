package com.lunacode.hook;

public final class PromptHookActionExecutor implements HookActionExecutor {
    @Override
    public HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        if (!(hook.action() instanceof HookAction.Prompt prompt)) {
            return HookActionResult.failure("Hook action 不是 prompt", java.util.Map.of("errorType", "invalid_action"));
        }
        return HookActionResult.success(prompt.prompt());
    }
}
