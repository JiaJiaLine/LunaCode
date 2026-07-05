package com.lunacode.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FileSystemSkillSource implements SkillSource {
    public static final int PROJECT_PRIORITY = 300;
    public static final int USER_PRIORITY = 200;

    private final SkillSourceKind kind;
    private final int priority;
    private final Path fixedRoot;

    public FileSystemSkillSource(SkillSourceKind kind) {
        this(kind, null);
    }

    public FileSystemSkillSource(SkillSourceKind kind, Path fixedRoot) {
        if (kind != SkillSourceKind.PROJECT && kind != SkillSourceKind.USER) {
            throw new IllegalArgumentException("文件系统来源只能是 PROJECT 或 USER");
        }
        this.kind = kind;
        this.priority = kind == SkillSourceKind.PROJECT ? PROJECT_PRIORITY : USER_PRIORITY;
        this.fixedRoot = fixedRoot == null ? null : fixedRoot.toAbsolutePath().normalize();
    }

    public static FileSystemSkillSource project() {
        return new FileSystemSkillSource(SkillSourceKind.PROJECT);
    }

    public static FileSystemSkillSource user() {
        return new FileSystemSkillSource(SkillSourceKind.USER);
    }

    @Override
    public List<SkillCandidate> discover(Path projectRoot, Path userHome) {
        Path root = skillRoot(projectRoot, userHome);
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        List<SkillCandidate> candidates = new ArrayList<>();
        try (var stream = Files.list(root)) {
            List<Path> children = stream
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path child : children) {
                if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(".md")) {
                    candidates.add(SkillCandidate.singleFile(child, origin(child)));
                    continue;
                }
                if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("SKILL.md"))) {
                    candidates.add(SkillCandidate.directory(child, origin(child.resolve("SKILL.md"))));
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(candidates);
    }

    private Path skillRoot(Path projectRoot, Path userHome) {
        if (fixedRoot != null) {
            return fixedRoot;
        }
        return switch (kind) {
            case PROJECT -> projectRoot == null ? null : projectRoot.resolve(".lunacode").resolve("skills");
            case USER -> userHome == null ? null : userHome.resolve(".lunacode").resolve("skills");
            case BUILTIN -> null;
        };
    }

    private SkillOrigin origin(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return new SkillOrigin(kind, normalized.toString(), java.util.Optional.of(normalized), priority);
    }
}
