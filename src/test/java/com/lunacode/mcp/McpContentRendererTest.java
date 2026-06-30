package com.lunacode.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.tool.SensitiveValueMasker;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class McpContentRendererTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rendersTextStructuredAndMasksSensitiveValues() {
        ObjectNode result = mapper.createObjectNode();
        result.putArray("content").addObject().put("type", "text").put("text", "token=secret-value");
        result.putObject("structuredContent").put("ok", true);
        SensitiveValueMasker masker = new SensitiveValueMasker();
        masker.add("secret-value");
        ToolExecutionContext context = new ToolExecutionContext(Path.of("."), Duration.ofSeconds(1), 1000, masker);

        ToolResult rendered = new McpContentRenderer().render("srv", "tool", McpToolCallResult.success(result), context);

        assertFalse(rendered.isError());
        assertTrue(rendered.content().contains("[MASKED]"));
        assertTrue(rendered.content().contains("结构化内容摘要"));
    }
}
