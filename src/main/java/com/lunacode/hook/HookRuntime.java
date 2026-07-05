package com.lunacode.hook;

import java.util.Optional;

public interface HookRuntime {
    void emit(HookEventName event, HookContext context, HookExecutionScope scope);

    Optional<HookRejection> runPreToolHooks(HookContext context, HookExecutionScope scope);

    void enqueueReminder(String sessionId, PendingHookReminder reminder);

    default void close() {
    }
}
