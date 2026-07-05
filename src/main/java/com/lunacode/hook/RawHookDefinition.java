package com.lunacode.hook;

import java.util.Map;

public record RawHookDefinition(
        HookSource source,
        int order,
        String event,
        String condition,
        Map<String, Object> action,
        Boolean reject,
        Boolean async,
        Boolean once,
        Integer timeoutMs,
        Boolean injectResult
) {
    public RawHookDefinition {
        action = action == null ? Map.of() : Map.copyOf(action);
    }
}
