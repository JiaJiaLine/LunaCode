package com.lunacode.hook;

import java.util.List;
import java.util.Objects;

public record HookCondition(HookConditionMode mode, List<HookPredicate> predicates, String rawExpression) {
    public HookCondition {
        mode = Objects.requireNonNull(mode, "mode");
        predicates = predicates == null ? List.of() : List.copyOf(predicates);
        rawExpression = rawExpression == null ? "" : rawExpression;
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("Hook 条件至少需要一个子条件");
        }
    }
}
