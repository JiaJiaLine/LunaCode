package com.lunacode.subagent;

import com.lunacode.background.BackgroundTaskManager;
import com.lunacode.background.ForegroundSubAgentTracker;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.tool.ToolResult;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DefaultSubAgentService implements SubAgentService {
    private final AgentDefinitionCatalog catalog;
    private final SubAgentRunnerFactory runnerFactory;
    private final BackgroundTaskManager backgroundTaskManager;
    private final ForegroundSubAgentTracker foregroundTracker;
    private final ProviderConfig providerConfig;

    public DefaultSubAgentService(
            AgentDefinitionCatalog catalog,
            SubAgentRunnerFactory runnerFactory,
            BackgroundTaskManager backgroundTaskManager,
            ForegroundSubAgentTracker foregroundTracker,
            ProviderConfig providerConfig
    ) {
        this.catalog = catalog;
        this.runnerFactory = runnerFactory;
        this.backgroundTaskManager = backgroundTaskManager;
        this.foregroundTracker = foregroundTracker;
        this.providerConfig = providerConfig;
    }

    @Override
    public ToolResult launchFromTool(AgentToolRequest request, SubAgentParentContext parentContext) {
        if (request == null || request.task().isBlank()) {
            return error("Agent 工具缺少 task", "invalid_arguments");
        }
        if (parentContext == null) {
            return error("Agent 工具缺少父运行上下文", "missing_parent_context");
        }
        if (parentContext.parentIsBackground()) {
            return error("后台 Agent 不能再 spawn Agent", "nested_agent_forbidden");
        }
        if (request.subagentType().isEmpty()) {
            if (parentContext.parentIsFork()) {
                return error("Fork 式子 Agent 不能再 Fork", "fork_cannot_fork");
            }
            SubAgentLaunchRequest launch = new SubAgentLaunchRequest(
                    SubAgentKind.FORK,
                    Optional.empty(),
                    request.task(),
                    true,
                    parentContext,
                    SubAgentNotificationPolicy.TOOL
            );
            return asyncResult(backgroundTaskManager.launch(launch), "fork");
        }

        Optional<AgentDefinition> definition = catalog.find(request.subagentType().orElseThrow());
        if (definition.isEmpty()) {
            return error("子 Agent 类型不存在: " + request.subagentType().orElse(""), "subagent_not_found");
        }
        boolean runInBackground = request.runInBackground() || definition.get().background();
        SubAgentLaunchRequest launch = new SubAgentLaunchRequest(
                SubAgentKind.DEFINED,
                definition,
                request.task(),
                runInBackground,
                parentContext,
                SubAgentNotificationPolicy.TOOL
        );
        if (runInBackground) {
            return asyncResult(backgroundTaskManager.launch(launch), definition.get().agentType());
        }
        return runForeground(launch);
    }

    @Override
    public String launchFromHook(String subagentType, String task, HookExecutionScope scope) {
        Optional<AgentDefinition> definition = catalog.find(subagentType);
        if (definition.isEmpty()) {
            throw new IllegalArgumentException("子 Agent 类型不存在: " + subagentType);
        }
        HookExecutionScope safeScope = scope == null ? new HookExecutionScope("unknown-session", 0, Path.of(".")) : scope;
        AgentRunConfig parentConfig = new AgentRunConfig(
                safeScope.workspaceRoot(),
                AgentMode.DEFAULT,
                PermissionMode.DEFAULT,
                safeScope.workspaceRoot().resolve(".lunacode/plan.md"),
                providerConfig.agent().maxIterations(),
                providerConfig.agent().maxConsecutiveUnknownTools(),
                Clock.systemDefaultZone()
        );
        SubAgentParentContext parentContext = new SubAgentParentContext(
                new DefaultConversationManager(),
                parentConfig,
                parentConfig.toolAccessPolicy(),
                false,
                false,
                safeScope.sessionId(),
                safeScope.workspaceRoot()
        );
        return backgroundTaskManager.launch(new SubAgentLaunchRequest(
                SubAgentKind.DEFINED,
                definition,
                task,
                true,
                parentContext,
                SubAgentNotificationPolicy.HOOK
        ));
    }

    @Override
    public SubAgentRunHandle startForeground(SubAgentLaunchRequest request) {
        return runnerFactory.start(request);
    }

    @Override
    public String launchBackground(SubAgentLaunchRequest request) {
        return backgroundTaskManager.launch(request);
    }

    private ToolResult runForeground(SubAgentLaunchRequest launch) {
        SubAgentRunHandle handle = runnerFactory.start(launch);
        foregroundTracker.setCurrent(handle, launch.task());
        try {
            long timeout = providerConfig.agent().getAutoBackgroundMs();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
            while (true) {
                if (handle.backgroundTaskId().isPresent()) {
                    return asyncResult(handle.backgroundTaskId().orElseThrow(), launch.definition().map(AgentDefinition::agentType).orElse("defined"));
                }
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new TimeoutException("自动切入后台");
                }
                long waitMillis = Math.min(100L, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                try {
                    SubAgentResult result = handle.completion().get(waitMillis, TimeUnit.MILLISECONDS);
                    foregroundTracker.clear(handle);
                    if (result.failureReason().isPresent()) {
                        return error(result.fullResult(), "subagent_failed");
                    }
                    return ToolResult.success(result.fullResult(), Map.of(
                            "status", "completed",
                            "summary", result.summary(),
                            "toolCallCount", result.toolCallCount()
                    ));
                } catch (TimeoutException ignored) {
                    // 短轮询用于观察 ESC/adoptRunning，不代表自动后台超时。
                }
            }
        } catch (TimeoutException e) {
            String taskId = foregroundTracker.adoptCurrentToBackground()
                    .orElseGet(() -> backgroundTaskManager.adoptRunning(handle, launch.task()));
            return asyncResult(taskId, launch.definition().map(AgentDefinition::agentType).orElse("defined"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            foregroundTracker.clear(handle);
            return error("子 Agent 前台等待被中断", "interrupted");
        } catch (ExecutionException e) {
            foregroundTracker.clear(handle);
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return error("子 Agent 执行失败: " + cause.getMessage(), "subagent_failed");
        } finally {
            foregroundTracker.clear(handle);
        }
    }

    private ToolResult asyncResult(String taskId, String agentType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "async_launched");
        metadata.put("taskId", taskId);
        metadata.put("agentType", agentType);
        return ToolResult.success("async_launched\nagent_id: " + taskId, metadata);
    }

    private ToolResult error(String message, String errorType) {
        return ToolResult.error(message, Map.of("errorType", errorType));
    }
}
