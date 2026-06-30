package com.lunacode.config;

public record SandboxRootConfig(
        String name,
        String path
) {
    public SandboxRootConfig {
        name = name == null ? "" : name.strip();
        path = path == null ? "" : path.strip();
    }
}
