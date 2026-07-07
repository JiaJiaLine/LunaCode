package com.lunacode.tool;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public final class ToolExecutionScopeHolder {
    private static final ThreadLocal<ToolExecutionScope> CURRENT = new ThreadLocal<>();

    private ToolExecutionScopeHolder() {
    }

    public static Optional<ToolExecutionScope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Optional<Path> currentWorkDir() {
        return current().map(ToolExecutionScope::workDir);
    }

    public static <T> T withScope(ToolExecutionScope scope, Supplier<T> supplier) {
        ToolExecutionScope previous = CURRENT.get();
        if (scope == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(scope);
        }
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
