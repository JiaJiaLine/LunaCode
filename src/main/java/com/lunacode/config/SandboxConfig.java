package com.lunacode.config;

import java.util.List;

public record SandboxConfig(
        boolean networkEnabled,
        List<SandboxRootConfig> extraRoots
) {
    public SandboxConfig {
        extraRoots = extraRoots == null ? List.of() : List.copyOf(extraRoots);
    }

    public static SandboxConfig defaults() {
        return new SandboxConfig(false, List.of());
    }
}
