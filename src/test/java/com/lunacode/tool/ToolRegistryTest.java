package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registersEnablesDisablesAndBuildsClaudeTools() {
        DefaultToolRegistry registry = defaultRegistry();

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
    void apiFormatContainsEveryRegisteredToolName() {
        DefaultToolRegistry registry = defaultRegistry();

        Set<String> names = StreamSupport.stream(registry.toAPIFormat().spliterator(), false)
                .map(node -> node.path("name").asText())
                .collect(Collectors.toSet());

        assertEquals(Set.of("ReadFile", "WriteFile", "EditFile", "Bash", "Glob", "Grep"), names);
    }

    @Test
    void resolvesCommonAliasesFromModelResponses() {
        DefaultToolRegistry registry = defaultRegistry();

        assertTrue(registry.get("write").isPresent());
        assertEquals("WriteFile", registry.get("write").orElseThrow().name());
        assertEquals("WriteFile", registry.get("write_file").orElseThrow().name());
        assertEquals("WriteFile", registry.get("writefile").orElseThrow().name());
        assertEquals("Bash", registry.get("shell").orElseThrow().name());
    }

    @Test
    void executorAcceptsWriteAlias() throws Exception {
        Path workspace = Files.createTempDirectory("lunacode-tools-");
        try {
            WorkspacePathResolver resolver = new WorkspacePathResolver(workspace);
            DefaultToolRegistry registry = new DefaultToolRegistry();
            registry.register(new WriteFileTool(resolver));
            ToolExecutionContext context = new ToolExecutionContext(workspace, java.time.Duration.ofSeconds(1), 1000, new SensitiveValueMasker());
            DefaultToolExecutor executor = new DefaultToolExecutor(registry, context);
            JsonNode input = mapper.createObjectNode().put("path", "alias.txt").put("content", "ok");

            ToolResult result = executor.execute(new ToolUse("1", "write", input));

            assertFalse(result.isError(), result.content());
            assertEquals("ok", Files.readString(workspace.resolve("alias.txt")));
        } finally {
            Files.deleteIfExists(workspace.resolve("alias.txt"));
            Files.deleteIfExists(workspace);
        }
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

    private DefaultToolRegistry defaultRegistry() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        WorkspacePathResolver resolver = new WorkspacePathResolver(Path.of(".").toAbsolutePath().normalize());
        registry.register(new ReadFileTool(resolver));
        registry.register(new WriteFileTool(resolver));
        registry.register(new EditFileTool(resolver));
        registry.register(new BashTool());
        registry.register(new GlobTool(resolver));
        registry.register(new GrepTool(resolver));
        return registry;
    }
}