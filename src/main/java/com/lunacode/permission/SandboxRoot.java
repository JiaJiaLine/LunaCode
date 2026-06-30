package com.lunacode.permission;

import com.lunacode.config.SandboxConfig;
import com.lunacode.config.SandboxRootConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record SandboxRoot(
        String name,
        Path realPath,
        String virtualPrefix
) {
    private static final Pattern ROOT_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    public SandboxRoot {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("沙箱根目录 name 不能为空");
        }
        name = name.strip();
        realPath = Objects.requireNonNull(realPath, "realPath").toAbsolutePath().normalize();
        if (virtualPrefix == null || virtualPrefix.isBlank() || !virtualPrefix.startsWith("/")) {
            throw new IllegalArgumentException("virtualPrefix 必须以 / 开头");
        }
        virtualPrefix = virtualPrefix.strip();
    }

    public static List<SandboxRoot> build(Path workspaceRoot, SandboxConfig config) {
        List<SandboxRoot> roots = new ArrayList<>();
        roots.add(project(workspaceRoot));
        if (config != null) {
            for (SandboxRootConfig extraRoot : config.extraRoots()) {
                roots.add(extra(extraRoot));
            }
        }
        return List.copyOf(roots);
    }

    public static SandboxRoot project(Path workspaceRoot) {
        return new SandboxRoot("project", realPath(workspaceRoot), "/project");
    }

    public static SandboxRoot extra(SandboxRootConfig config) {
        if (config == null || config.name().isBlank() || config.path().isBlank()) {
            throw new IllegalArgumentException("额外沙箱根目录必须包含 name 和 path");
        }
        if (!ROOT_NAME.matcher(config.name()).matches()) {
            throw new IllegalArgumentException("额外沙箱根目录 name 只允许字母、数字、下划线和连字符: " + config.name());
        }
        return new SandboxRoot(config.name(), realPath(Path.of(config.path())), "/roots/" + config.name());
    }

    private static Path realPath(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("沙箱根目录不存在: " + path);
            }
            return path.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("沙箱根目录解析失败: " + path, e);
        }
    }
}
