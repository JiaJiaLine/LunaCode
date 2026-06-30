package com.lunacode.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlPermissionRuleStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsThreeLayersAndIgnoresOnlyBrokenLayer() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path userHome = tempDir.resolve("home");
        Files.createDirectories(workspace.resolve(".lunacode"));
        Files.createDirectories(userHome.resolve(".lunacode"));
        Files.writeString(userHome.resolve(".lunacode/permissions.yaml"), """
                - rule: "Bash(git *)"
                  effect: allow
                """);
        Files.writeString(workspace.resolve(".lunacode/permissions.yaml"), "not: a-list");
        Files.writeString(workspace.resolve(".lunacode/permissions.local.yaml"), """
                - rule: "Bash(rm *)"
                  effect: deny
                """);

        LoadedPermissionRules loaded = new YamlPermissionRuleStore(workspace, userHome).load();

        assertEquals(1, loaded.userRules().size());
        assertEquals(0, loaded.projectRules().size());
        assertEquals(1, loaded.localRules().size());
        assertFalse(loaded.warnings().isEmpty());
    }

    @Test
    void appendsLocalAllowAndRefusesBrokenLocalFile() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path userHome = tempDir.resolve("home");
        YamlPermissionRuleStore store = new YamlPermissionRuleStore(workspace, userHome);

        assertTrue(store.appendLocalAllow("Bash(git status --short)").success());
        assertTrue(Files.readString(workspace.resolve(".lunacode/permissions.local.yaml")).contains("Bash(git status --short)"));

        Files.writeString(workspace.resolve(".lunacode/permissions.local.yaml"), "broken: [");
        assertFalse(store.appendLocalAllow("Bash(git *)").success());
    }
}
