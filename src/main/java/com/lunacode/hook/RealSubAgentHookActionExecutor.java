package com.lunacode.hook;

import com.lunacode.subagent.SubAgentService;

import java.util.Map;
import java.util.function.Supplier;

public final class RealSubAgentHookActionExecutor implements HookActionExecutor {
    private final Supplier<SubAgentService> subAgentServiceSupplier;

    public RealSubAgentHookActionExecutor(SubAgentService subAgentService) {
        this(() -> subAgentService);
    }

    public RealSubAgentHookActionExecutor(Supplier<SubAgentService> subAgentServiceSupplier) {
        this.subAgentServiceSupplier = subAgentServiceSupplier;
    }

    @Override
    public HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        if (!(hook.action() instanceof HookAction.SubAgent subAgent)) {
            return HookActionResult.failure("Hook action 不是 sub_agent", Map.of("errorType", "invalid_action"));
        }
        SubAgentService subAgentService = subAgentServiceSupplier == null ? null : subAgentServiceSupplier.get();
        if (subAgentService == null) {
            return HookActionResult.failure("sub_agent 执行器尚未完成初始化", Map.of("errorType", "sub_agent_not_ready"));
        }
        try {
            String taskId = subAgentService.launchFromHook(subAgent.name(), subAgent.prompt(), scope);
            return HookActionResult.success(
                    "sub_agent 已启动后台任务: " + taskId,
                    Map.of("taskId", taskId, "subAgent", subAgent.name())
            );
        } catch (RuntimeException e) {
            return HookActionResult.failure(
                    "sub_agent 启动失败: " + e.getMessage(),
                    Map.of("errorType", "sub_agent_failed", "subAgent", subAgent.name())
            );
        }
    }
}