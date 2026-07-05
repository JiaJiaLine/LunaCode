package com.lunacode.hook;

import java.util.Optional;

public final class NoOpHookRuntime implements HookRuntime {
    private static final NoOpHookRuntime INSTANCE = new NoOpHookRuntime();

    private NoOpHookRuntime() {
    }

    public static NoOpHookRuntime instance() {
        return INSTANCE;
    }

    @Override
    public void emit(HookEventName event, HookContext context, HookExecutionScope scope) {
    }

    @Override
    public Optional<HookRejection> runPreToolHooks(HookContext context, HookExecutionScope scope) {
        return Optional.empty();
    }

    @Override
    public void enqueueReminder(String sessionId, PendingHookReminder reminder) {
    }
}
