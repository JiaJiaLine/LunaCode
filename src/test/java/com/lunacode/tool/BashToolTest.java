package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BashToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void commandSuccessNonZeroAndTimeoutAreStructured() {
        BashTool tool = new BashTool();
        ToolResult success = tool.execute(context(Duration.ofSeconds(3), new SensitiveValueMasker()), mapper.createObjectNode().put("command", "echo hello"));
        assertFalse(success.isError());
        assertEquals(0, success.metadata().get("exitCode"));
        assertTrue(success.content().contains("hello"));

        ToolResult failed = tool.execute(context(Duration.ofSeconds(3), new SensitiveValueMasker()), mapper.createObjectNode().put("command", failingCommand()));
        assertTrue(failed.isError());
        assertEquals(false, failed.metadata().get("timedOut"));

        ToolResult timeout = tool.execute(context(Duration.ofMillis(300), new SensitiveValueMasker()), mapper.createObjectNode().put("command", slowCommand()).put("timeout_seconds", 1));
        assertTrue(timeout.isError());
        assertEquals(true, timeout.metadata().get("timedOut"));
    }

    @Test
    void blacklistedCommandNeverStartsProcess() {
        BashTool tool = new BashTool();
        Path output = tempDir.resolve("blocked.txt");

        ToolResult result = tool.execute(context(tempDir, Duration.ofSeconds(3), new SensitiveValueMasker()),
                mapper.createObjectNode().put("command", "echo should-not-run > blocked.txt && rm -rf /"));

        assertTrue(result.isError());
        assertEquals("blacklisted_command", result.metadata().get("errorType"));
        assertFalse(Files.exists(output));
    }
    @Test
    void masksSensitiveOutput() {
        SensitiveValueMasker masker = new SensitiveValueMasker(java.util.List.of("SECRET_TOKEN"));
        BashTool tool = new BashTool();
        ToolResult result = tool.execute(context(Duration.ofSeconds(3), masker), mapper.createObjectNode().put("command", "echo SECRET_TOKEN"));
        assertFalse(result.content().contains("SECRET_TOKEN"));
    }

    @Test
    void redirectionCreatesFileInWorkspace() throws Exception {
        BashTool tool = new BashTool();
        String command = "echo Hello World > tool-output.txt 2>&1";

        ToolResult result = tool.execute(context(tempDir, Duration.ofSeconds(3), new SensitiveValueMasker()),
                mapper.createObjectNode().put("command", command));

        assertFalse(result.isError(), result.content());
        Path output = tempDir.resolve("tool-output.txt");
        assertTrue(Files.exists(output), "redirection should create a file in the workspace");
        assertTrue(Files.readString(output).contains("Hello World"));
    }

    private ToolExecutionContext context(Duration timeout, SensitiveValueMasker masker) {
        return context(Path.of(".").toAbsolutePath().normalize(), timeout, masker);
    }

    private ToolExecutionContext context(Path workspaceRoot, Duration timeout, SensitiveValueMasker masker) {
        return new ToolExecutionContext(workspaceRoot, timeout, 10_000, masker);
    }

    private String failingCommand() {
        return isWindows() ? "exit /b 7" : "exit 7";
    }

    private String slowCommand() {
        return isWindows() ? "ping -n 5 127.0.0.1 > nul" : "sleep 5";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}