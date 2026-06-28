package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AskUserQuestionToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void asksBrokerAndWrapsAnswer() {
        ToolExecutionContext context = new ToolExecutionContext(Path.of("."), Duration.ofSeconds(1), 1000, new SensitiveValueMasker(), request -> "Java");
        ToolResult result = new AskUserQuestionTool().execute(context, mapper.createObjectNode().put("question", "Which language?"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Java"));
    }

    @Test
    void validatesQuestionAndBroker() {
        ToolExecutionContext context = new ToolExecutionContext(Path.of("."), Duration.ofSeconds(1), 1000, new SensitiveValueMasker());
        assertTrue(new AskUserQuestionTool().execute(context, mapper.createObjectNode().put("question", " ")).isError());
        assertTrue(new AskUserQuestionTool().execute(context, mapper.createObjectNode().put("question", "Need answer?")).isError());
    }
}