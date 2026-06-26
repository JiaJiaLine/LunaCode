package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir Path tempDir;

    @Test
    void readsWithLineNumbersOffsetLimitAndMetadata() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "one\ntwo\nthree\n");
        ReadFileTool tool = new ReadFileTool(new WorkspacePathResolver(tempDir));
        ObjectNode input = mapper.createObjectNode().put("path", "a.txt").put("offset", 2).put("limit", 2);

        ToolResult result = tool.execute(context(), input);

        assertFalse(result.isError());
        assertEquals("2\ttwo\n3\tthree\n", result.content());
        assertEquals(2, result.metadata().get("startLine"));
        assertEquals(3, result.metadata().get("totalLines"));
    }

    @Test
    void missingFileAndOutsidePathReturnErrors() {
        ReadFileTool tool = new ReadFileTool(new WorkspacePathResolver(tempDir));
        ToolResult missing = tool.execute(context(), mapper.createObjectNode().put("path", "missing.txt"));
        assertTrue(missing.isError());

        ToolResult outside = tool.execute(context(), mapper.createObjectNode().put("path", "../secret.txt"));
        assertTrue(outside.isError());
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(tempDir, Duration.ofSeconds(1), 10_000, new SensitiveValueMasker());
    }
}