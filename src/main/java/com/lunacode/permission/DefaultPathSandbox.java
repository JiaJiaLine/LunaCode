package com.lunacode.permission;

import com.lunacode.config.SandboxConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DefaultPathSandbox implements PathSandbox {
    private final Path workspaceRoot;
    private final List<SandboxRoot> roots;

    public DefaultPathSandbox(Path workspaceRoot, SandboxConfig config) {
        this(workspaceRoot, SandboxRoot.build(workspaceRoot, config));
    }

    public DefaultPathSandbox(Path workspaceRoot, List<SandboxRoot> roots) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        this.roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        if (this.roots.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个沙箱根目录");
        }
    }

    @Override
    public Result validate(String requestedPath, PathIntent intent) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return Result.deny("路径不能为空");
        }
        try {
            Path candidate = resolveRequested(requestedPath.strip());
            Path realCandidate = realCandidate(candidate, intent);
            SandboxRoot root = matchingRoot(realCandidate);
            if (root == null) {
                return Result.deny("路径超出允许目录: " + requestedPath);
            }
            return Result.allow(toVirtualPath(root, realCandidate));
        } catch (Exception e) {
            return Result.deny("路径沙箱校验失败: " + requestedPath + " (" + e.getMessage() + ")");
        }
    }

    @Override
    public List<SandboxRoot> roots() {
        return roots;
    }

    private Path resolveRequested(String requestedPath) {
        for (SandboxRoot root : roots) {
            if (requestedPath.equals(root.virtualPrefix())) {
                return root.realPath();
            }
            String prefix = root.virtualPrefix() + "/";
            if (requestedPath.startsWith(prefix)) {
                return root.realPath().resolve(requestedPath.substring(prefix.length())).normalize();
            }
        }
        Path raw = Path.of(requestedPath);
        return raw.isAbsolute() ? raw.normalize() : workspaceRoot.resolve(raw).normalize();
    }

    private Path realCandidate(Path candidate, PathIntent intent) throws IOException {
        if (Files.exists(candidate)) {
            return candidate.toRealPath();
        }
        if (intent != PathIntent.WRITE && intent != PathIntent.COMMAND_ARGUMENT && intent != PathIntent.GLOB) {
            Path parent = candidate.getParent();
            if (parent == null) {
                throw new IOException("找不到父目录");
            }
            return realCandidateForMissingPath(candidate);
        }
        return realCandidateForMissingPath(candidate);
    }

    private Path realCandidateForMissingPath(Path candidate) throws IOException {
        Path current = candidate;
        List<Path> missing = new ArrayList<>();
        while (current != null && !Files.exists(current)) {
            Path fileName = current.getFileName();
            if (fileName != null) {
                missing.add(fileName);
            }
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("找不到已存在的父目录");
        }
        Path real = current.toRealPath();
        Collections.reverse(missing);
        for (Path part : missing) {
            real = real.resolve(part.toString()).normalize();
        }
        return real;
    }

    private SandboxRoot matchingRoot(Path path) {
        SandboxRoot best = null;
        for (SandboxRoot root : roots) {
            if (path.startsWith(root.realPath())
                    && (best == null || root.realPath().getNameCount() > best.realPath().getNameCount())) {
                best = root;
            }
        }
        return best;
    }

    private VirtualPath toVirtualPath(SandboxRoot root, Path realPath) {
        Path relative = root.realPath().relativize(realPath);
        String rel = relative.toString().replace('\\', '/');
        String virtual = rel.isBlank() ? root.virtualPrefix() : root.virtualPrefix() + "/" + rel;
        return new VirtualPath(root, realPath, virtual);
    }
}
