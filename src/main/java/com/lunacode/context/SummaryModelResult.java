package com.lunacode.context;

public record SummaryModelResult(
        boolean success,
        String finalSummary,
        FailureType failureType,
        String failureReason
) {
    public enum FailureType {
        PROMPT_TOO_LONG,
        OTHER
    }

    public static SummaryModelResult success(String finalSummary) {
        return new SummaryModelResult(true, finalSummary, null, null);
    }

    public static SummaryModelResult failure(FailureType type, String reason) {
        return new SummaryModelResult(false, null, type, reason);
    }
}
