package com.lunacode.hook;

import java.util.Map;

public final class DefaultHookActionExecutor implements HookActionExecutor {
    private final HookActionExecutor commandExecutor;
    private final HookActionExecutor promptExecutor;
    private final HookActionExecutor httpExecutor;
    private final HookActionExecutor subAgentExecutor;

    public DefaultHookActionExecutor(
            HookActionExecutor commandExecutor,
            HookActionExecutor promptExecutor,
            HookActionExecutor httpExecutor,
            HookActionExecutor subAgentExecutor
    ) {
        this.commandExecutor = commandExecutor;
        this.promptExecutor = promptExecutor == null ? new PromptHookActionExecutor() : promptExecutor;
        this.httpExecutor = httpExecutor == null ? new HttpHookActionExecutor() : httpExecutor;
        this.subAgentExecutor = subAgentExecutor == null ? new SubAgentPlaceholderActionExecutor() : subAgentExecutor;
    }

    @Override
    public HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        if (hook == null || hook.action() == null) {
            return HookActionResult.failure("Hook action 缺失", Map.of("errorType", "missing_action"));
        }
        return switch (hook.action().type()) {
            case COMMAND -> commandExecutor == null
                    ? HookActionResult.failure("command Hook 执行器未配置", Map.of("errorType", "executor_not_configured"))
                    : commandExecutor.execute(hook, context, scope);
            case PROMPT -> promptExecutor.execute(hook, context, scope);
            case HTTP -> httpExecutor.execute(hook, context, scope);
            case SUB_AGENT -> subAgentExecutor.execute(hook, context, scope);
        };
    }
}
