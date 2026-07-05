package com.lunacode.hook;

import java.util.Objects;

public record HookPredicate(String field, HookOperator operator, String expected) {
    public HookPredicate {
        field = Objects.requireNonNull(field, "field").strip();
        operator = Objects.requireNonNull(operator, "operator");
        expected = expected == null ? "" : expected;
    }
}
