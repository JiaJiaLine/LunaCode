package com.lunacode.permission;

import java.nio.file.Path;
import java.util.Objects;

public record VirtualPath(
        SandboxRoot root,
        Path realPath,
        String virtualPath
) {
    public VirtualPath {
        root = Objects.requireNonNull(root, "root");
        realPath = Objects.requireNonNull(realPath, "realPath").toAbsolutePath().normalize();
        if (virtualPath == null || virtualPath.isBlank() || !virtualPath.startsWith("/")) {
            throw new IllegalArgumentException("virtualPath 必须以 / 开头");
        }
        virtualPath = virtualPath.replace('\\', '/');
    }
}
