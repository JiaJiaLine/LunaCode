package com.lunacode.orchestrator;

public record StatusSnapshot(
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String errorSummary
) {
    public static StatusSnapshot idle(String provider, String model) {
        return new StatusSnapshot(provider, model, null, null, "idle", null);
    }
}