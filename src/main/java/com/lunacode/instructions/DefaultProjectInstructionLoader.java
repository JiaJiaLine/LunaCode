package com.lunacode.instructions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DefaultProjectInstructionLoader implements ProjectInstructionLoader {
    private final IncludeResolver includeResolver;

    public DefaultProjectInstructionLoader() {
        this(new IncludeResolver());
    }

    public DefaultProjectInstructionLoader(IncludeResolver includeResolver) {
        this.includeResolver = includeResolver;
    }

    @Override
    public ProjectInstructionContext load(Path projectRoot, Path userHome) {
        Path root = projectRoot == null ? Path.of("").toAbsolutePath().normalize() : projectRoot.toAbsolutePath().normalize();
        Path home = userHome == null ? Path.of(System.getProperty("user.home")) : userHome.toAbsolutePath().normalize();
        Path userLunaRoot = home.resolve(".lunacode").normalize();
        List<InstructionSection> sections = new ArrayList<>();
        addIfExists(sections, new InstructionSource(root.resolve("LUNACODE.md"), InstructionScope.PROJECT_ROOT, 100), new IncludeBoundary(root, "项目根目录"));
        addIfExists(sections, new InstructionSource(root.resolve(".lunacode").resolve("LUNACODE.md"), InstructionScope.PROJECT_LOCAL, 50), new IncludeBoundary(root, "项目根目录"));
        addIfExists(sections, new InstructionSource(userLunaRoot.resolve("LUNACODE.md"), InstructionScope.USER, 10), new IncludeBoundary(userLunaRoot, "用户级 ~/.lunacode"));
        return new ProjectInstructionContext(sections);
    }

    private void addIfExists(List<InstructionSection> sections, InstructionSource source, IncludeBoundary boundary) {
        if (!Files.exists(source.path())) {
            return;
        }
        sections.add(new InstructionSection(source, includeResolver.expand(source.path(), boundary)));
    }
}
