package com.lunacode.tool;

import com.lunacode.config.SandboxConfig;
import com.lunacode.config.SandboxRootConfig;
import com.lunacode.permission.SandboxRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BubblewrapCommandSandboxTest {
    @TempDir
    Path tempDir;

    @Test
    void wrapsCommandWithBubblewrapSeccompNetworkAndBinds() throws Exception {
        Path extra = tempDir.resolve("cache");
        Files.createDirectories(extra);
        SandboxConfig config = new SandboxConfig(false, List.of(new SandboxRootConfig("cache", extra.toString())));
        List<SandboxRoot> roots = SandboxRoot.build(tempDir, config);
        BubblewrapCommandSandbox sandbox = new BubblewrapCommandSandbox(false, Path.of("/usr/bin/bwrap"));

        CommandSandbox.PreparedCommand prepared = sandbox.wrapShellCommand("echo hi", tempDir, roots, config);
        String script = String.join(" ", prepared.command());

        assertTrue(script.contains("bwrap"));
        assertTrue(script.contains("--seccomp 3"));
        assertTrue(script.contains("--unshare-net"));
        assertTrue(script.contains(tempDir.toRealPath().toString()));
        assertTrue(script.contains(extra.toRealPath().toString()));
    }

    @Test
    void omitsUnshareNetWhenNetworkIsEnabledAndFailsWhenBwrapMissing() {
        BubblewrapCommandSandbox sandbox = new BubblewrapCommandSandbox(false, Path.of("/usr/bin/bwrap"));
        CommandSandbox.PreparedCommand prepared = sandbox.wrapShellCommand("echo hi", tempDir, SandboxRoot.build(tempDir, SandboxConfig.defaults()), new SandboxConfig(true, List.of()));
        assertFalse(String.join(" ", prepared.command()).contains("--unshare-net"));

        BubblewrapCommandSandbox unavailable = new BubblewrapCommandSandbox(true, tempDir.resolve("missing-bwrap"));
        assertTrue(unavailable.wrapShellCommand("echo hi", tempDir, List.of(), SandboxConfig.defaults()).isError());
    }
}
