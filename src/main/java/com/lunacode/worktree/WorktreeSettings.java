package com.lunacode.worktree;

import java.util.List;

public record WorktreeSettings(
        List<String> localConfigFiles,
        List<String> symlinkDirectories,
        List<String> warnings
) {
    public WorktreeSettings {
        localConfigFiles = localConfigFiles == null ? List.of("settings.local.json") : List.copyOf(localConfigFiles);
        symlinkDirectories = symlinkDirectories == null ? List.of() : List.copyOf(symlinkDirectories);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static WorktreeSettings defaults() {
        return new WorktreeSettings(List.of("settings.local.json"), List.of(), List.of());
    }
}
