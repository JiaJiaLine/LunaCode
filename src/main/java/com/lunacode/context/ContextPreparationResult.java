package com.lunacode.context;

public record ContextPreparationResult(
        boolean proceed,
        boolean compacted,
        CompactTrigger trigger,
        long estimatedTokensBefore,
        long estimatedTokensAfter,
        int externalizedToolResults,
        int summarizedMessages,
        int restoredFiles,
        String userVisibleMessage
) {
    public static ContextPreparationResult proceed(CompactTrigger trigger) {
        return new ContextPreparationResult(true, false, trigger, 0, 0, 0, 0, 0, null);
    }
}
