package com.lunacode.prompt;

import com.lunacode.runtime.AgentRunConfig;

import com.lunacode.runtime.AgentMode;

import com.lunacode.conversation.ApiMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageChannelBuilderTest {
    @Test
    void defaultsProjectInstructionsAndMemoryToEmptyAndKeepsHistoryOrder() {
        AgentRunConfig config = new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of("plan.md"), 1, 1, Clock.systemUTC());
        List<ApiMessage> history = List.of(new ApiMessage("user", "one"), new ApiMessage("assistant", "two"));
        ModeInjectionState state = new ModeInjectionState(AgentMode.DEFAULT, 1, config.planFile(), 3);

        MessageChannel channel = new MessageChannelBuilder().build(config, state, history);

        assertTrue(channel.projectInstructions().isEmpty());
        assertTrue(channel.memory().isEmpty());
        assertTrue(channel.reminders().isEmpty());
        assertEquals(history, channel.history());
    }
}
