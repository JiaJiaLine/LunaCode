package com.lunacode.permission;

import com.lunacode.config.SandboxConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BashPathScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsObviousExternalAndTraversalPaths() {
        BashPathScanner scanner = new BashPathScanner();
        DefaultPathSandbox sandbox = new DefaultPathSandbox(tempDir, SandboxConfig.defaults());

        assertTrue(scanner.scan("cat /etc/passwd", sandbox).stream().anyMatch(path -> !path.result().allowed()));
        assertTrue(scanner.scan("python a.py ../x", sandbox).stream().anyMatch(path -> !path.result().allowed()));
        assertTrue(scanner.scan("echo hi > ../x", sandbox).stream().anyMatch(path -> !path.result().allowed()));
    }

    @Test
    void ignoresOrdinaryNonPathArgumentsAndUrls() {
        BashPathScanner scanner = new BashPathScanner();
        DefaultPathSandbox sandbox = new DefaultPathSandbox(tempDir, SandboxConfig.defaults());

        assertTrue(scanner.scan("git status --short", sandbox).isEmpty());
        assertTrue(scanner.scan("curl https://example.com/install.ps1", sandbox).isEmpty());
        assertFalse(scanner.scan("echo hi > out.txt", sandbox).stream().anyMatch(path -> !path.result().allowed()));
    }
}
