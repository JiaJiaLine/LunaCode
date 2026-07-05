package com.lunacode.hook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HookConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsProjectUserAndLocalInOrder() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path home = tempDir.resolve("home");
        Files.createDirectories(workspace.resolve(".lunacode"));
        Files.createDirectories(home.resolve(".lunacode"));
        Files.writeString(workspace.resolve(".lunacode/config.yaml"), hookYaml("turn_start", "project"));
        Files.writeString(home.resolve(".lunacode/config.yaml"), hookYaml("turn_start", "user"));
        Files.writeString(workspace.resolve(".lunacode/config.local.yaml"), hookYaml("turn_start", "local"));

        HookConfig config = new HookConfigLoader().load(workspace, home);

        assertEquals(List.of(HookSourceLevel.PROJECT, HookSourceLevel.USER, HookSourceLevel.LOCAL),
                config.hooks().stream().map(hook -> hook.source().level()).toList());
    }

    @Test
    void ignoresFilesWithoutHooks() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path home = tempDir.resolve("home");
        Files.createDirectories(workspace.resolve(".lunacode"));
        Files.createDirectories(home);
        Files.writeString(workspace.resolve(".lunacode/config.yaml"), "protocol: anthropic\n");

        HookConfig config = new HookConfigLoader().load(workspace, home);

        assertTrue(config.isEmpty());
    }

    private String hookYaml(String event, String text) {
        return """
                hooks:
                  - event: %s
                    action:
                      type: prompt
                      prompt: "%s"
                """.formatted(event, text);
    }
}
