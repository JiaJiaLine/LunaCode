package com.lunacode.context;

import java.util.List;

public record LightweightCompactionResult(
        int externalizedCount,
        List<ExternalizedToolResultRef> externalizedToolResults
) {
    public LightweightCompactionResult {
        externalizedToolResults = externalizedToolResults == null ? List.of() : List.copyOf(externalizedToolResults);
    }

    public static LightweightCompactionResult empty() {
        return new LightweightCompactionResult(0, List.of());
    }
}
