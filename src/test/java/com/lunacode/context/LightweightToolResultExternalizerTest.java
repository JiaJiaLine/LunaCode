package com.lunacode.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.tool.SensitiveValueMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LightweightToolResultExternalizerTest {
    @TempDir
    Path tempDir;

    @Test
    void externalizesSingleOversizedToolResultAndKeepsReadablePointer() throws Exception {
        DefaultConversationManager conversation = new DefaultConversationManager();
        conversation.addMessage(com.lunacode.conversation.MessageRole.USER, "读文件");
        conversation.addAssistantMessage(List.of(new ContentBlock.ToolUseBlock("toolu_1", "ReadFile", new ObjectMapper().createObjectNode().put("path", "big.txt"))));
        String original = "api_key=secret-token\n" + "A".repeat(3_000);
        conversation.addUserToolResultMessage(List.of(new ContentBlock.ToolResultBlock("toolu_1", original, false)));
        ProjectSessionContextStore store = new ProjectSessionContextStore(tempDir, tempDir.resolve("context"), "session-a", new SensitiveValueMasker(List.of("secret-token")));
        ContextConfig config = new ContextConfig(20_000, 1_000, 1_000, 1_000, 100, 10_000, 100, 1, 5, 5_000, 25_000, 3, 3, 0.2, tempDir.resolve("context"));

        LightweightCompactionResult result = new LightweightToolResultExternalizer().externalizeOversizedResults(
                conversation.fullSnapshot(),
                config,
                store,
                conversation
        );

        assertEquals(1, result.externalizedCount());
        Path externalized = result.externalizedToolResults().get(0).path();
        assertTrue(Files.exists(externalized));
        assertEquals(original, Files.readString(externalized));
        String toolMessage = conversation.fullSnapshot().get(2).content();
        assertTrue(toolMessage.contains("完整结果路径"));
        assertTrue(toolMessage.contains("重新读取提示"));
        assertFalse(toolMessage.contains("secret-token"));
        assertFalse(toolMessage.contains(original));
    }
}
