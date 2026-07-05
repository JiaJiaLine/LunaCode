package com.lunacode.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontmatterSkillParserTest {
    @TempDir
    Path tempDir;

    private final FrontmatterSkillParser parser = new FrontmatterSkillParser();

    @Test
    void parsesSingleFileWithDefaults() throws Exception {
        Path file = tempDir.resolve("commit.md");
        Files.writeString(file, """
                ---
                name: commit
                description: 生成提交信息
                ---
                请生成 commit。
                """);

        SkillDefinition definition = success(parser.parseSingleFile(file, origin(file)));

        assertEquals("commit", definition.name());
        assertEquals("生成提交信息", definition.description());
        assertEquals(SkillExecutionMode.INLINE, definition.mode());
        assertEquals(SkillContextPolicy.FULL, definition.context());
        assertTrue(definition.tools().isEmpty());
        assertTrue(definition.model().isEmpty());
        assertTrue(definition.body().contains("请生成 commit"));
    }

    @Test
    void parsesDirectorySkillWithResourceRoot() throws Exception {
        Path dir = tempDir.resolve("backend-interview");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), """
                ---
                name: backend-interview
                description: 面试题生成
                mode: fork
                context: recent
                tools:
                  - ReadFile
                model: luna-strong
                ---
                读取 examples/sample.md。
                """);

        SkillDefinition definition = success(parser.parseDirectory(dir, origin(dir.resolve("SKILL.md"))));

        assertEquals(SkillExecutionMode.FORK, definition.mode());
        assertEquals(SkillContextPolicy.RECENT, definition.context());
        assertEquals(Optional.of("luna-strong"), definition.model());
        assertEquals(java.util.List.of("ReadFile"), definition.tools());
        assertEquals(dir.toAbsolutePath().normalize(), definition.resourceRoot().orElseThrow());
    }

    @Test
    void rejectsMissingRequiredFieldsAndInvalidValues() throws Exception {
        assertFailure("""
                ---
                description: 缺名称
                ---
                body
                """);
        assertFailure("""
                ---
                name: missing-description
                ---
                body
                """);
        assertFailure("""
                ---
                name: BadName
                description: 非法名称
                ---
                body
                """);
        assertFailure("""
                ---
                name: bad-mode
                description: 非法模式
                mode: async
                ---
                body
                """);
        assertFailure("""
                ---
                name: bad-context
                description: 非法上下文
                context: latest
                ---
                body
                """);
    }

    @Test
    void rejectsMissingFrontmatter() throws Exception {
        Path file = tempDir.resolve("bad.md");
        Files.writeString(file, "no frontmatter");

        assertInstanceOf(SkillParseResult.Failure.class, parser.parseSingleFile(file, origin(file)));
    }

    private void assertFailure(String content) throws Exception {
        Path file = tempDir.resolve("skill-" + System.nanoTime() + ".md");
        Files.writeString(file, content);
        assertInstanceOf(SkillParseResult.Failure.class, parser.parseSingleFile(file, origin(file)));
    }

    private SkillDefinition success(SkillParseResult result) {
        return assertInstanceOf(SkillParseResult.Success.class, result).definition();
    }

    private SkillOrigin origin(Path path) {
        return new SkillOrigin(SkillSourceKind.PROJECT, path.toString(), Optional.of(path), 300);
    }
}
