package com.lunacode.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemSkillSourceTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversSingleFileAndDirectoryCandidates() throws Exception {
        Path root = tempDir.resolve("skills");
        Files.createDirectories(root);
        Files.writeString(root.resolve("commit.md"), "---\nname: commit\ndescription: d\n---\nbody");
        Path directorySkill = root.resolve("backend-interview");
        Files.createDirectories(directorySkill);
        Files.writeString(directorySkill.resolve("SKILL.md"), "---\nname: backend-interview\ndescription: d\n---\nbody");
        Files.createDirectories(root.resolve("ignored"));

        FileSystemSkillSource source = new FileSystemSkillSource(SkillSourceKind.PROJECT, root);
        java.util.List<SkillCandidate> candidates = source.discover(tempDir, tempDir);

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.kind() == SkillCandidate.Kind.SINGLE_FILE));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.kind() == SkillCandidate.Kind.DIRECTORY));
        assertTrue(candidates.stream().allMatch(candidate -> candidate.origin().kind() == SkillSourceKind.PROJECT));
        assertTrue(candidates.stream().allMatch(candidate -> candidate.origin().priority() == FileSystemSkillSource.PROJECT_PRIORITY));
    }

    @Test
    void missingRootReturnsEmptyCandidates() {
        FileSystemSkillSource source = new FileSystemSkillSource(SkillSourceKind.USER, tempDir.resolve("missing"));

        assertTrue(source.discover(tempDir, tempDir).isEmpty());
    }
}
