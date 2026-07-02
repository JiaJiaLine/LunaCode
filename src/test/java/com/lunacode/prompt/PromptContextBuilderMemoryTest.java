package com.lunacode.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.instructions.ProjectInstructionContext;
import com.lunacode.memory.MemoryIndexSnapshot;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptContextBuilderMemoryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void injectsMemoryIndexBeforeHistory() {
        AgentRunConfig config = new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of("plan.md"), 1, 1, Clock.systemUTC());
        PromptContextBuilder builder = new PromptContextBuilder(
                new StaticSystemPromptBuilder(),
                new EnvironmentContextCollector(),
                new MessageChannelBuilder(),
                (projectRoot, userHome) -> new ProjectInstructionContext(List.of()),
                () -> new MemoryIndexSnapshot("", "", "# 用户级记忆\n- [abc] user_preference | 偏好 | 用户喜欢中文回复", 2, 64)
        );

        PromptBundle bundle = builder.build(config, 1, List.of(new ApiMessage("user", "hi")), mapper.createArrayNode());

        assertTrue(bundle.messages().memory().isPresent());
        assertTrue(bundle.messages().memory().get().render().contains("用户喜欢中文回复"));
        assertTrue(bundle.messages().history().get(0).textContent().contains("hi"));
    }
}
