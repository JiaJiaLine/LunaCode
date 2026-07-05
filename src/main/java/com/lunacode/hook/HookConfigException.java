package com.lunacode.hook;

import java.util.List;

public class HookConfigException extends RuntimeException {
    private final List<String> errors;

    public HookConfigException(List<String> errors) {
        super(format(errors));
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }

    private static String format(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Hook 配置无效";
        }
        return "Hook 配置无效:\n- " + String.join("\n- ", errors);
    }
}
