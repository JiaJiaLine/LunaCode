package com.lunacode.context;

import java.nio.file.Path;
import java.time.Instant;

public record CompactionMetadata(
        CompactTrigger trigger,
        Instant compactedAt,
        int summarizedMessages,
        int retainedMessages,
        int externalizedToolResults,
        int restoredFiles,
        Path sessionLogPath
) {}
