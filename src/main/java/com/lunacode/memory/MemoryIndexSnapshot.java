package com.lunacode.memory;

public record MemoryIndexSnapshot(
        String userIndex,
        String projectIndex,
        String mergedContent,
        int lineCount,
        int byteCount
) {
    public MemoryIndexSnapshot {
        userIndex = userIndex == null ? "" : userIndex;
        projectIndex = projectIndex == null ? "" : projectIndex;
        mergedContent = mergedContent == null ? "" : mergedContent;
    }

    public static MemoryIndexSnapshot empty() {
        return new MemoryIndexSnapshot("", "", "", 0, 0);
    }
}
