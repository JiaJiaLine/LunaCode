package com.lunacode.hook;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record HookDefinition(
        String id,
        HookSource source,
        int order,
        HookEventName event,
        Optional<HookCondition> condition,
        HookAction action,
        boolean reject,
        boolean async,
        boolean once,
        Optional<Duration> timeout,
        boolean injectResult
) {
    public HookDefinition {
        id = Objects.requireNonNull(id, "id");
        source = Objects.requireNonNull(source, "source");
        event = Objects.requireNonNull(event, "event");
        condition = condition == null ? Optional.empty() : condition;
        action = Objects.requireNonNull(action, "action");
        timeout = timeout == null ? Optional.empty() : timeout;
    }
}
