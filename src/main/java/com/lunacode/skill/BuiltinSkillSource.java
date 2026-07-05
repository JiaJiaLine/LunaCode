package com.lunacode.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BuiltinSkillSource implements SkillSource {
    public static final int BUILTIN_PRIORITY = 100;

    private final ClassLoader classLoader;
    private final List<String> resources;

    public BuiltinSkillSource() {
        this(BuiltinSkillSource.class.getClassLoader(), List.of(
                "lunacode/skills/commit.md",
                "lunacode/skills/test.md"
        ));
    }

    public BuiltinSkillSource(ClassLoader classLoader, List<String> resources) {
        this.classLoader = classLoader == null ? BuiltinSkillSource.class.getClassLoader() : classLoader;
        this.resources = resources == null ? List.of() : List.copyOf(resources);
    }

    @Override
    public List<SkillCandidate> discover(java.nio.file.Path projectRoot, java.nio.file.Path userHome) {
        List<SkillCandidate> candidates = new ArrayList<>();
        for (String resource : resources) {
            try (var stream = classLoader.getResourceAsStream(resource)) {
                if (stream == null) {
                    continue;
                }
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                SkillOrigin origin = new SkillOrigin(
                        SkillSourceKind.BUILTIN,
                        resource,
                        java.util.Optional.empty(),
                        BUILTIN_PRIORITY
                );
                candidates.add(SkillCandidate.builtin(resource, content, origin));
            } catch (IOException ignored) {
                // 单个内置资源异常只跳过该资源，保持其余 Skill 可用。
            }
        }
        return List.copyOf(candidates);
    }
}
