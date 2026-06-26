package com.lunacode.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorkspacePathResolver {
    private final Path workspaceRoot;

    public WorkspacePathResolver(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public Path resolveInsideWorkspace(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        Path raw = Path.of(requestedPath);
        Path resolved = raw.isAbsolute() ? raw.normalize() : workspaceRoot.resolve(raw).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("路径超出工作区: " + requestedPath);
        }
        try {
            Path existing = Files.exists(resolved) ? resolved : nearestExistingParent(resolved);
            if (existing != null) {
                Path realRoot = workspaceRoot.toRealPath();
                Path realExisting = existing.toRealPath();
                if (!realExisting.startsWith(realRoot)) {
                    throw new IllegalArgumentException("路径符号链接逃逸工作区: " + requestedPath);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("路径解析失败: " + requestedPath, e);
        }
        return resolved;
    }

    private Path nearestExistingParent(Path path) {
        Path current = path.getParent();
        while (current != null) {
            if (Files.exists(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public String relativize(Path path) {
        return workspaceRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
