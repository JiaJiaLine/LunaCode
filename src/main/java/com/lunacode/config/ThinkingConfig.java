package com.lunacode.config;

public record ThinkingConfig(boolean enabled, Integer budgetTokens) {
    public static ThinkingConfig disabled() {
        return new ThinkingConfig(false, null);
    }
}