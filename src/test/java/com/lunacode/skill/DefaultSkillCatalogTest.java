package com.lunacode.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSkillCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void higherPrioritySourceOverridesLowerPriority() throws Exception {
        Path project = tempDir.resolve("project");
        Path user = tempDir.resolve("user");
        Path projectSkills = project.resolve(".lunacode/skills");
        Path userSkills = user.resolve(".lunacode/skills");
        Files.createDirectories(projectSkills);
        Files.createDirectories(userSkills);
        Files.writeString(projectSkills.resolve("commit.md"), skill("commit", "project"));
        Files.writeString(userSkills.resolve("commit.md"), skill("commit", "user"));

        SkillCatalog catalog = catalog(project, user, builtin("commit", "builtin"));
        assertTrue(catalog.loadForExecution("commit").orElseThrow().body().contains("project"));

        Files.delete(projectSkills.resolve("commit.md"));
        assertTrue(catalog.loadForExecution("commit").orElseThrow().body().contains("user"));

        Files.delete(userSkills.resolve("commit.md"));
        assertTrue(catalog.loadForExecution("commit").orElseThrow().body().contains("builtin"));
    }

    @Test
    void badSkillDoesNotBlockOtherSkills() throws Exception {
        Path project = tempDir.resolve("project");
        Path skills = project.resolve(".lunacode/skills");
        Files.createDirectories(skills);
        Files.writeString(skills.resolve("bad.md"), "---\nname: Bad\ndescription: bad\n---\nbody");
        Files.writeString(skills.resolve("good.md"), skill("good", "ok"));

        SkillCatalogSnapshot snapshot = catalog(project, tempDir).snapshot();

        assertEquals(List.of("good"), snapshot.summaries().stream().map(SkillSummary::name).toList());
        assertEquals(1, snapshot.diagnostics().size());
    }

    @Test
    void commandConflictAndMissingToolMakeSkillInvalid() throws Exception {
        Path project = tempDir.resolve("project");
        Path skills = project.resolve(".lunacode/skills");
        Files.createDirectories(skills);
        Files.writeString(skills.resolve("review.md"), skill("review", "conflict"));
        Files.writeString(skills.resolve("missing-tool.md"), """
                ---
                name: missing-tool
                description: 缂哄伐鍏?                tools:
                  - NoSuchTool
                ---
                body
                """);

        SkillCatalogSnapshot snapshot = catalog(project, tempDir).snapshot();

        assertTrue(snapshot.summaries().isEmpty());
        assertEquals(2, snapshot.diagnostics().size());
    }

    @Test
    void reloadsChangedFileAndFallsBackToCacheOnParseFailure() throws Exception {
        Path project = tempDir.resolve("project");
        Path skills = project.resolve(".lunacode/skills");
        Files.createDirectories(skills);
        Path file = skills.resolve("commit.md");
        Files.writeString(file, skill("commit", "v1"));

        SkillCatalog catalog = catalog(project, tempDir);
        assertTrue(catalog.loadForExecution("commit").orElseThrow().body().contains("v1"));

        Files.writeString(file, skill("commit", "v2"));
        assertTrue(catalog.loadForExecution("commit").orElseThrow().body().contains("v2"));

        Files.writeString(file, "---\nname: Bad\n");
        assertTrue(catalog.loadForExecution("commit").orElseThrow().body().contains("v2"));
        assertTrue(catalog.diagnostics().stream().anyMatch(diagnostic -> diagnostic.sourceId().contains("commit.md")));
    }

    private SkillCatalog catalog(Path project, Path user, SkillSource... extraSources) {
        List<SkillSource> sources = new java.util.ArrayList<>();
        sources.add(FileSystemSkillSource.user());
        sources.add(FileSystemSkillSource.project());
        sources.addAll(List.of(extraSources));
        return new DefaultSkillCatalog(
                sources,
                new FrontmatterSkillParser(),
                project,
                user,
                () -> Set.of("/review", "/help"),
                () -> Set.of("ReadFile", "Bash", "LoadSkill")
        );
    }

    private SkillSource builtin(String name, String body) {
        return (projectRoot, userHome) -> List.of(SkillCandidate.builtin(
                "builtin-" + name,
                skill(name, body),
                new SkillOrigin(SkillSourceKind.BUILTIN, "builtin-" + name, Optional.empty(), BuiltinSkillSource.BUILTIN_PRIORITY)
        ));
    }

    private String skill(String name, String body) {
        return """
                ---
                name: %s
                description: %s skill
                ---
                body %s
                """.formatted(name, name, body);
    }
}


