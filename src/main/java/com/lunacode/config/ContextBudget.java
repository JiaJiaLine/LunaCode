package com.lunacode.config;

public record ContextBudget(
        long contextWindowTokens,
        long summaryOutputReserveTokens,
        long effectiveWindowTokens,
        long autoCompactThresholdTokens,
        long forceCompactThresholdTokens
) {
    public ContextBudget {
        if (contextWindowTokens <= 0) {
            throw new IllegalArgumentException("contextWindowTokens 必须大于 0");
        }
        if (summaryOutputReserveTokens <= 0) {
            throw new IllegalArgumentException("summaryOutputReserveTokens 必须大于 0");
        }
        if (effectiveWindowTokens <= 0) {
            throw new IllegalArgumentException("effectiveWindowTokens 必须大于 0");
        }
        if (autoCompactThresholdTokens <= 0) {
            throw new IllegalArgumentException("autoCompactThresholdTokens 必须大于 0");
        }
        if (forceCompactThresholdTokens <= autoCompactThresholdTokens) {
            throw new IllegalArgumentException("forceCompactThresholdTokens 必须大于 autoCompactThresholdTokens");
        }
        if (effectiveWindowTokens >= contextWindowTokens) {
            throw new IllegalArgumentException("effectiveWindowTokens 必须小于 contextWindowTokens");
        }
    }
}
