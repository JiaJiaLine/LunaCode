package com.lunacode.subagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FileSystemAgentDefinitionSource implements AgentDefinitionSource {
    private final AgentDefinitionSourceKind kind;
    private final Path fixedRoot;

    public FileSystemAgentDefinitionSource(AgentDefinitionSourceKind kind) {
        this(kind, null);
    }

    public FileSystemAgentDefinitionSource(AgentDefinitionSourceKind kind, Path fixedRoot) {
        if (kind != AgentDefinitionSourceKind.PROJECT && kind != AgentDefinitionSourceKind.USER) {
            throw new IllegalArgumentException("文件系统 Agent 来源只能是 PROJECT 或 USER");
        }
        this.kind = kind;
        this.fixedRoot = fixedRoot == null ? null : fixedRoot.toAbsolutePath().normalize();
    }

    public static FileSystemAgentDefinitionSource project() {
        return new FileSystemAgentDefinitionSource(AgentDefinitionSourceKind.PROJECT);
    }

    public static FileSystemAgentDefinitionSource user() {
        return new FileSystemAgentDefinitionSource(AgentDefinitionSourceKind.USER);
    }

    @Override
    public List<AgentDefinitionCandidate> discover(Path projectRoot, Path userHome) {
        Path root = root(projectRoot, userHome);
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        List<AgentDefinitionCandidate> candidates = new ArrayList<>();
        try (var stream = Files.list(root)) {
            for (Path child : stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(".md")) {
                    candidates.add(AgentDefinitionCandidate.file(kind, child));
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(candidates);
    }

    private Path root(Path projectRoot, Path userHome) {
        if (fixedRoot != null) {
            return fixedRoot;
        }
        return switch (kind) {
            case PROJECT -> projectRoot == null ? null : projectRoot.resolve(".lunacode").resolve("agents");
            case USER -> userHome == null ? null : userHome.resolve(".lunacode").resolve("agents");
            default -> null;
        };
    }
}
