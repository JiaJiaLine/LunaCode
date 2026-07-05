package com.lunacode.hook;

import java.util.List;

public record HookConfig(List<HookDefinition> hooks) {
    public HookConfig {
        hooks = hooks == null ? List.of() : List.copyOf(hooks);
    }

    public static HookConfig empty() {
        return new HookConfig(List.of());
    }

    public boolean isEmpty() {
        return hooks.isEmpty();
    }
}
