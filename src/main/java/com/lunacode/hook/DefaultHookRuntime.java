package com.lunacode.hook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DefaultHookRuntime implements HookRuntime {
    private static final String FALLBACK_REJECTION = "Hook 拒绝了工具调用，但动作没有返回可用原因。";

    private final HookConfig config;
    private final HookConditionEvaluator evaluator;
    private final HookActionExecutor actionExecutor;
    private final HookOnceTracker onceTracker;
    private final HookReminderStore reminderStore;
    private final HookLogWriter logWriter;
    private final ExecutorService executor;

    public DefaultHookRuntime(
            HookConfig config,
            HookConditionEvaluator evaluator,
            HookActionExecutor actionExecutor,
            HookOnceTracker onceTracker,
            HookReminderStore reminderStore,
            HookLogWriter logWriter
    ) {
        this.config = config == null ? HookConfig.empty() : config;
        this.evaluator = evaluator == null ? new HookConditionEvaluator() : evaluator;
        this.actionExecutor = actionExecutor;
        this.onceTracker = onceTracker == null ? new InMemoryHookOnceTracker() : onceTracker;
        this.reminderStore = reminderStore == null ? new InMemoryHookReminderStore() : reminderStore;
        this.logWriter = logWriter == null ? (sessionId, entry) -> {} : logWriter;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "lunacode-hook");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void emit(HookEventName event, HookContext context, HookExecutionScope scope) {
        HookExecutionScope safeScope = safeScope(scope);
        HookContext safeContext = safeContext(event, context);
        for (HookDefinition hook : matchingHooks(event, safeContext, safeScope)) {
            if (hook.async()) {
                executor.submit(() -> runHook(hook, safeContext, safeScope));
            } else {
                runHook(hook, safeContext, safeScope);
            }
        }
    }

    @Override
    public Optional<HookRejection> runPreToolHooks(HookContext context, HookExecutionScope scope) {
        HookExecutionScope safeScope = safeScope(scope);
        HookContext safeContext = safeContext(HookEventName.PRE_TOOL_USE, context);
        for (HookDefinition hook : matchingHooks(HookEventName.PRE_TOOL_USE, safeContext, safeScope)) {
            HookActionResult result = executeSafely(hook, safeContext, safeScope);
            if (hook.reject()) {
                String reason = result.output().isBlank() || !result.success() && result.output().isBlank()
                        ? FALLBACK_REJECTION
                        : result.output();
                log(hook, safeScope, "rejected", result, "");
                return Optional.of(new HookRejection(hook.id(), safeContext.toolName(), reason));
            }
            handleReminder(hook, result, safeScope);
        }
        return Optional.empty();
    }

    @Override
    public void enqueueReminder(String sessionId, PendingHookReminder reminder) {
        reminderStore.add(sessionId, reminder);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void runHook(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        HookActionResult result = executeSafely(hook, context, scope);
        handleReminder(hook, result, scope);
    }

    private HookActionResult executeSafely(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        long started = System.nanoTime();
        try {
            if (actionExecutor == null) {
                HookActionResult result = HookActionResult.failure("Hook 动作执行器未配置", java.util.Map.of("errorType", "executor_not_configured"));
                log(hook, scope, "failed", result, "");
                return result;
            }
            HookActionResult result = actionExecutor.execute(hook, context, scope);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            logWriter.log(scope.sessionId(), new HookLogEntry(
                    Instant.now(),
                    hook.id(),
                    hook.event().yamlName(),
                    hook.action().type().yamlName(),
                    result.success() ? "success" : "failed",
                    durationMillis,
                    result.output(),
                    result.success() ? "" : result.output(),
                    result.metadata()
            ));
            return result;
        } catch (Exception e) {
            HookActionResult result = HookActionResult.failure("Hook 执行失败: " + e.getMessage(), e);
            log(hook, scope, "failed", result, e.getMessage());
            return result;
        }
    }

    private void handleReminder(HookDefinition hook, HookActionResult result, HookExecutionScope scope) {
        if (result == null || result.output().isBlank()) {
            return;
        }
        boolean shouldInject = hook.action().type() == HookActionType.PROMPT || hook.injectResult();
        if (!shouldInject) {
            return;
        }
        int availableTurn = hook.event() == HookEventName.PRE_SEND ? Math.max(1, scope.turnIndex()) : Math.max(1, scope.turnIndex() + 1);
        reminderStore.add(scope.sessionId(), new PendingHookReminder(hook.id(), result.output(), availableTurn));
    }

    private List<HookDefinition> matchingHooks(HookEventName event, HookContext context, HookExecutionScope scope) {
        return config.hooks().stream()
                .filter(hook -> hook.event() == event)
                .filter(hook -> matchesCondition(hook, context))
                .filter(hook -> markOnce(hook, scope))
                .toList();
    }

    private boolean matchesCondition(HookDefinition hook, HookContext context) {
        boolean matches = hook.condition().map(condition -> evaluator.matches(condition, context)).orElse(true);
        if (!matches) {
            log(hook, safeScope(null), "skipped", HookActionResult.success("condition_not_matched"), "");
        }
        return matches;
    }

    private boolean markOnce(HookDefinition hook, HookExecutionScope scope) {
        if (!hook.once()) {
            return true;
        }
        boolean first = onceTracker.markIfFirst(scope.sessionId(), hook.id());
        if (!first) {
            log(hook, scope, "skipped", HookActionResult.success("once_already_executed"), "");
        }
        return first;
    }

    private HookContext safeContext(HookEventName event, HookContext context) {
        HookContext base = context == null ? HookContext.empty(event) : context;
        return base.withEvent(event);
    }

    private HookExecutionScope safeScope(HookExecutionScope scope) {
        return scope == null ? new HookExecutionScope("unknown-session", 0, java.nio.file.Path.of(".")) : scope;
    }

    private void log(HookDefinition hook, HookExecutionScope scope, String status, HookActionResult result, String error) {
        HookExecutionScope safeScope = safeScope(scope);
        logWriter.log(safeScope.sessionId(), new HookLogEntry(
                Instant.now(),
                hook == null ? "" : hook.id(),
                hook == null ? "" : hook.event().yamlName(),
                hook == null ? "" : hook.action().type().yamlName(),
                status,
                0,
                result == null ? "" : result.output(),
                error == null ? "" : error,
                result == null ? java.util.Map.of() : result.metadata()
        ));
    }
}
