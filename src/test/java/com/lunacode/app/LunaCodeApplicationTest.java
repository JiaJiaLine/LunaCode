package com.lunacode.app;

import com.lunacode.config.SandboxConfig;
import com.lunacode.permission.SandboxRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LunaCodeApplicationTest {
    @TempDir
    Path tempDir;

    @Test
    void addsUserSkillRootAsReadOnlySandboxRootWhenPresent() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path userHome = tempDir.resolve("home");
        Files.createDirectories(workspace);
        Files.createDirectories(userHome.resolve(".lunacode/skills"));

        Path expectedRoot = userHome.resolve(".lunacode/skills").toRealPath();
        List<SandboxRoot> roots = LunaCodeApplication.buildSandboxRoots(workspace, userHome, SandboxConfig.defaults());

        assertTrue(roots.stream().anyMatch(root ->
                root.name().equals("user-skills")
                        && root.readOnly()
                        && root.virtualPrefix().equals("/roots/user-skills")
                        && root.realPath().equals(expectedRoot)
        ));
    }
}
