package com.lunacode.hook;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HookValidatorTest {
    private final HookSource source = new HookSource(HookSourceLevel.PROJECT, Path.of("config.yaml"));

    @Test
    void normalizesValidHook() {
        HookConfig config = new HookValidator().validate(List.of(new RawHookDefinition(
                source,
                1,
                "post_tool_use",
                "tool == WriteFile",
                Map.of("type", "command", "command", "echo ok"),
                null,
                null,
                true,
                1000,
                true
        )));

        HookDefinition hook = config.hooks().get(0);
        assertEquals(HookEventName.POST_TOOL_USE, hook.event());
        assertTrue(hook.once());
        assertTrue(hook.injectResult());
        assertTrue(hook.timeout().isPresent());
    }

    @Test
    void aggregatesValidationErrors() {
        HookConfigException ex = assertThrows(HookConfigException.class, () -> new HookValidator().validate(List.of(
                new RawHookDefinition(source, 1, null, null, Map.of("type", "command", "command", "x"), null, null, null, null, null),
                new RawHookDefinition(source, 2, "turn_start", "A == B && C == D || E == F", Map.of("type", "prompt", "prompt", "x"), true, null, null, null, null),
                new RawHookDefinition(source, 3, "pre_tool_use", null, Map.of("type", "command", "command", "x"), null, true, null, null, null)
        )));
        assertEquals(3, ex.errors().size());
        assertTrue(ex.getMessage().contains("reject 只能用于 pre_tool_use"));
        assertTrue(ex.getMessage().contains("pre_tool_use Hook 不允许 async"));
    }

    @Test
    void acceptsSubAgentPlaceholderShape() {
        HookConfig config = new HookValidator().validate(List.of(new RawHookDefinition(
                source,
                1,
                "turn_start",
                null,
                Map.of("type", "sub_agent", "name", "worker", "prompt", "do it"),
                null,
                null,
                null,
                null,
                null
        )));
        assertEquals(HookActionType.SUB_AGENT, config.hooks().get(0).action().type());
    }
}
