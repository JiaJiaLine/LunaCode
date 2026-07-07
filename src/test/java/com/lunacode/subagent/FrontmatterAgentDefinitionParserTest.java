package com.lunacode.subagent;

import com.lunacode.permission.PermissionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontmatterAgentDefinitionParserTest {
    @TempDir
    Path tempDir;

    private final FrontmatterAgentDefinitionParser parser = new FrontmatterAgentDefinitionParser();

    @Test
    void parsesAgentDefinitionFrontmatter() throws Exception {
        Path file = tempDir.resolve("security-reviewer.md");
        Files.writeString(file, """
                ---
                name: security-reviewer
                description: 专注安全审查
                tools:
                  - ReadFile
                  - Grep
                disallowedTools:
                  - Bash
                model: sonnet
                maxTurns: 20
                permissionMode: dontAsk
                isolation: worktree
                background: true
                ---
                你是安全审查 Agent。
                """);

        AgentDefinition definition = success(parser.parse(AgentDefinitionCandidate.file(AgentDefinitionSourceKind.PROJECT, file)));

        assertEquals("security-reviewer", definition.agentType());
        assertEquals("专注安全审查", definition.whenToUse());
        assertEquals(java.util.List.of("ReadFile", "Grep"), definition.tools());
        assertEquals(java.util.List.of("Bash"), definition.disallowedTools());
        assertEquals("sonnet", definition.model());
        assertEquals(20, definition.maxTurns().orElseThrow());
        assertEquals(PermissionMode.DEFAULT, definition.permissionMode().orElseThrow());
        assertEquals(AgentIsolation.WORKTREE, definition.isolation());
        assertTrue(definition.background());
        assertTrue(definition.systemPrompt().contains("安全审查"));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        Path file = tempDir.resolve("bad.md");
        Files.writeString(file, """
                ---
                description: 缺少名称
                ---
                body
                """);

        assertInstanceOf(AgentDefinitionParseResult.Failure.class, parser.parse(AgentDefinitionCandidate.file(AgentDefinitionSourceKind.PROJECT, file)));
    }

    private AgentDefinition success(AgentDefinitionParseResult result) {
        return assertInstanceOf(AgentDefinitionParseResult.Success.class, result).definition();
    }
}