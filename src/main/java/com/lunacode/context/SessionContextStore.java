package com.lunacode.context;

import java.nio.file.Path;

public interface SessionContextStore {
    Path sessionDirectory();

    Path writeToolResult(ExternalizedToolResultPayload payload);

    Path writeSessionLog(SessionLogSnapshot snapshot);

    Path writeCompactionMetadata(CompactionMetadata metadata);
}
