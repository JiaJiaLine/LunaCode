package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registersEnablesDisablesAndBuildsClaudeTools() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        WorkspacePathResolver resolver = new WorkspacePathResolver(Path.of(".").toAbsolutePath().normalize());
        registry.register(new ReadFileTool(resolver));
        registry.register(new WriteFileTool(resolver));
        registry.register(new EditFileTool(resolver));
        registry.register(new BashTool());
        registry.register(new GlobTool(resolver));
        registry.register(new GrepTool(resolver));

        assertEquals(6, registry.getEnabledTools().size());
        assertEquals("ReadFile", registry.toAPIFormat().get(0).path("name").asText());
        assertTrue(registry.toAPIFormat().get(0).has("input_schema"));

        registry.disable("ReadFile");
        assertTrue(registry.get("ReadFile").isEmpty());
        assertEquals(5, registry.getEnabledTools().size());
        registry.enable("ReadFile");
        assertTrue(registry.get("ReadFile").isPresent());
    }

    @Test
    void executorWrapsUnknownAndInvalidArguments() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        ToolExecutionContext context = new ToolExecutionContext(Path.of("."), java.time.Duration.ofSeconds(1), 1000, new SensitiveValueMasker());
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, context);

        ToolResult unknown = executor.execute(new ToolUse("1", "Missing", mapper.createObjectNode()));
        assertTrue(unknown.isError());

        WorkspacePathResolver resolver = new WorkspacePathResolver(Path.of(".").toAbsolutePath().normalize());
        registry.register(new ReadFileTool(resolver));
        ToolResult invalid = executor.execute(new ToolUse("2", "ReadFile", mapper.createObjectNode()));
        assertTrue(invalid.isError());
        assertEquals("invalid_arguments", invalid.metadata().get("errorType"));
    }
}