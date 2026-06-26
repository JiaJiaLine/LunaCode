package com.lunacode.orchestrator;

public record StatusSnapshot(
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String errorSummary,
        String toolName,
        String toolSummary
) {
    public StatusSnapshot(String provider, String model, Integer inputTokens, Integer outputTokens, String state, String errorSummary) {
        this(provider, model, inputTokens, outputTokens, state, errorSummary, null, null);
    }

    public static StatusSnapshot idle(String provider, String model) {
        return new StatusSnapshot(provider, model, null, null, "idle", null, null, null);
    }
}
