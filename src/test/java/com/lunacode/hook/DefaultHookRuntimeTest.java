package com.lunacode.hook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultHookRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void promptReminderIsAvailableOnCurrentPreSendTurn() {
        InMemoryHookReminderStore store = new InMemoryHookReminderStore();
        DefaultHookRuntime runtime = runtime(List.of(hook("h1", HookEventName.PRE_SEND, new HookAction.Prompt("remember this"), false, false, false, false)), store, (hook, context, scope) -> HookActionResult.success("remember this"));

        runtime.emit(HookEventName.PRE_SEND, HookContext.empty(HookEventName.PRE_SEND), scope(1));

        assertEquals(1, store.drain("s1", 1).size());
    }

    @Test
    void onceHookRunsOnlyOncePerSession() {
        AtomicInteger calls = new AtomicInteger();
        DefaultHookRuntime runtime = runtime(List.of(hook("h1", HookEventName.TURN_START, new HookAction.Prompt("once"), false, false, true, false)), new InMemoryHookReminderStore(), (hook, context, scope) -> {
            calls.incrementAndGet();
            return HookActionResult.success("once");
        });

        runtime.emit(HookEventName.TURN_START, HookContext.empty(HookEventName.TURN_START), scope(1));
        runtime.emit(HookEventName.TURN_START, HookContext.empty(HookEventName.TURN_START), scope(2));

        assertEquals(1, calls.get());
    }

    @Test
    void preToolRejectReturnsReasonAndFallback() {
        DefaultHookRuntime runtime = runtime(List.of(hook("reject", HookEventName.PRE_TOOL_USE, new HookAction.Command("x"), true, false, false, false)), new InMemoryHookReminderStore(), (hook, context, scope) -> HookActionResult.success("blocked"));

        Optional<HookRejection> rejection = runtime.runPreToolHooks(new HookContext("pre_tool_use", "WriteFile", Map.of("path", "a.txt"), "a.txt", "", ""), scope(1));

        assertTrue(rejection.isPresent());
        assertEquals("blocked", rejection.get().reason());

        DefaultHookRuntime fallbackRuntime = runtime(List.of(hook("reject", HookEventName.PRE_TOOL_USE, new HookAction.Command("x"), true, false, false, false)), new InMemoryHookReminderStore(), (hook, context, scope) -> HookActionResult.failure("", Map.of()));
        HookRejection fallback = fallbackRuntime.runPreToolHooks(new HookContext("pre_tool_use", "WriteFile", Map.of(), "", "", ""), scope(1)).orElseThrow();
        assertTrue(fallback.reason().contains("Hook 拒绝"));
    }

    @Test
    void commandResultInjectsOnlyWhenEnabled() {
        InMemoryHookReminderStore store = new InMemoryHookReminderStore();
        DefaultHookRuntime runtime = runtime(List.of(
                hook("plain", HookEventName.POST_TOOL_USE, new HookAction.Command("x"), false, false, false, false),
                hook("inject", HookEventName.POST_TOOL_USE, new HookAction.Command("x"), false, false, false, true)
        ), store, (hook, context, scope) -> HookActionResult.success(hook.id()));

        runtime.emit(HookEventName.POST_TOOL_USE, HookContext.empty(HookEventName.POST_TOOL_USE), scope(1));

        assertEquals(1, store.drain("s1", 2).size());
    }

    private DefaultHookRuntime runtime(List<HookDefinition> hooks, InMemoryHookReminderStore store, HookActionExecutor executor) {
        return new DefaultHookRuntime(new HookConfig(hooks), new HookConditionEvaluator(), executor, new InMemoryHookOnceTracker(), store, (sessionId, entry) -> {});
    }

    private HookDefinition hook(String id, HookEventName event, HookAction action, boolean reject, boolean async, boolean once, boolean inject) {
        return new HookDefinition(id, new HookSource(HookSourceLevel.PROJECT, tempDir.resolve("config.yaml")), 1, event, Optional.empty(), action, reject, async, once, Optional.empty(), inject);
    }

    private HookExecutionScope scope(int turn) {
        return new HookExecutionScope("s1", turn, tempDir);
    }
}
