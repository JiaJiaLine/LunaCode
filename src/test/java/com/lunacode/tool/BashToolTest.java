package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BashToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
    void masksSensitiveOutput() {
        SensitiveValueMasker masker = new SensitiveValueMasker(java.util.List.of("SECRET_TOKEN"));
        BashTool tool = new BashTool();
        ToolResult result = tool.execute(context(Duration.ofSeconds(3), masker), mapper.createObjectNode().put("command", "echo SECRET_TOKEN"));
        assertFalse(result.content().contains("SECRET_TOKEN"));
    }

    private ToolExecutionContext context(Duration timeout, SensitiveValueMasker masker) {
        return new ToolExecutionContext(Path.of(".").toAbsolutePath().normalize(), timeout, 10_000, masker);
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