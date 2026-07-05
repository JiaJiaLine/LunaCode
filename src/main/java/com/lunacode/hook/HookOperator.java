package com.lunacode.hook;

import java.util.Optional;

public enum HookOperator {
    EQUALS("=="),
    NOT_EQUALS("!="),
    REGEX("=~"),
    GLOB("~=");

    private final String symbol;

    HookOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public static Optional<HookOperator> fromSymbol(String value) {
        for (HookOperator operator : values()) {
            if (operator.symbol.equals(value)) {
                return Optional.of(operator);
            }
        }
        return Optional.empty();
    }
}
