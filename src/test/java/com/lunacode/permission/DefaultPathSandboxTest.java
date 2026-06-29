package com.lunacode.permission;

import com.lunacode.config.SandboxConfig;
import com.lunacode.config.SandboxRootConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPathSandboxTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsProjectAndExtraRootsToVirtualPaths() throws Exception {
        Path extra = tempDir.resolve("cache");
        Files.createDirectories(extra);
        DefaultPathSandbox sandbox = new DefaultPathSandbox(
                tempDir,
                new SandboxConfig(false, List.of(new SandboxRootConfig("cache", extra.toString())))
        );

        assertEquals("/project/src/App.java", sandbox.validate("src/App.java", PathIntent.READ).path().virtualPath());
        assertEquals("/roots/cache/file.txt", sandbox.validate("/roots/cache/file.txt", PathIntent.WRITE).path().virtualPath());
    }

    @Test
    void rejectsTraversalAndExternalAbsolutePaths() throws Exception {
        DefaultPathSandbox sandbox = new DefaultPathSandbox(tempDir, SandboxConfig.defaults());
        Path outside = tempDir.getParent().resolve("outside.txt");
        Files.writeString(outside, "outside");

        assertFalse(sandbox.validate("../outside.txt", PathIntent.READ).allowed());
        assertFalse(sandbox.validate(outside.toString(), PathIntent.READ).allowed());
    }

    @Test
    void resolvesSymlinkBeforePrefixCheck() throws Exception {
        Path outside = tempDir.getParent().resolve("outside-symlink-target.txt");
        Files.writeString(outside, "outside");
        Path link = tempDir.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (Exception e) {
            Assumptions.abort("当前环境不允许创建符号链接: " + e.getMessage());
        }
        DefaultPathSandbox sandbox = new DefaultPathSandbox(tempDir, SandboxConfig.defaults());

        assertFalse(sandbox.validate("link.txt", PathIntent.READ).allowed());
    }

    @Test
    void checksExistingParentForNewFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("safe"));
        DefaultPathSandbox sandbox = new DefaultPathSandbox(tempDir, SandboxConfig.defaults());

        assertTrue(sandbox.validate("safe/new.txt", PathIntent.WRITE).allowed());
        assertFalse(sandbox.validate("../outside-new.txt", PathIntent.WRITE).allowed());
    }
}
