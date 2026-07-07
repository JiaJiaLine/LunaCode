package com.lunacode.tool;

import com.lunacode.permission.PathIntent;
import com.lunacode.permission.PathSandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorkspacePathResolver {
    private final Path workspaceRoot;
    private final PathSandbox pathSandbox;

    public WorkspacePathResolver(Path workspaceRoot) {
        this(workspaceRoot, null);
    }

    public WorkspacePathResolver(Path workspaceRoot, PathSandbox pathSandbox) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.pathSandbox = pathSandbox;
    }

    public Path resolveInsideWorkspace(String requestedPath) {
        return resolveInsideWorkspace(requestedPath, PathIntent.READ);
    }

    public Path resolveInsideWorkspace(String requestedPath, PathIntent intent) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        Path effectiveRoot = effectiveWorkspaceRoot();
        if (pathSandbox != null && effectiveRoot.equals(workspaceRoot)) {
            PathSandbox.Result result = pathSandbox.validate(requestedPath, intent == null ? PathIntent.READ : intent);
            if (!result.allowed()) {
                throw new IllegalArgumentException(result.reason());
            }
            return result.path().realPath();
        }
        Path raw = Path.of(requestedPath);
        Path resolved = raw.isAbsolute() ? raw.normalize() : effectiveRoot.resolve(raw).normalize();
        if (!resolved.startsWith(effectiveRoot)) {
            throw new IllegalArgumentException("路径超出工作区: " + requestedPath);
        }
        try {
            Path existing = Files.exists(resolved) ? resolved : nearestExistingParent(resolved);
            if (existing != null) {
                Path realRoot = effectiveRoot.toRealPath();
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
        Path normalized = path.toAbsolutePath().normalize();
        Path effectiveRoot = effectiveWorkspaceRoot();
        if (normalized.startsWith(effectiveRoot)) {
            return effectiveRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private Path effectiveWorkspaceRoot() {
        return ToolExecutionScopeHolder.currentWorkDir().orElse(workspaceRoot);
    }
}
