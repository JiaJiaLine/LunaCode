package com.lunacode.hook;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HookConditionEvaluatorTest {
    private final HookConditionParser parser = new HookConditionParser();
    private final HookConditionEvaluator evaluator = new HookConditionEvaluator();

    @Test
    void matchesOperatorsAndAliases() {
        HookContext context = new HookContext("pre_tool_use", "WriteFile", Map.of("path", "src/App.java"), "src/App.java", "hello", "");

        assertTrue(matches("tool == WriteFile", context));
        assertTrue(matches("tool != Bash", context));
        assertTrue(matches("args.path =~ .*App\\.java", context));
        assertTrue(matches("args.path ~= src/*.java", context));
        assertTrue(matches("tool == WriteFile && args.path ~= src/*.java", context));
        assertTrue(matches("tool == Bash || args.path ~= src/*.java", context));
        assertFalse(matches("missing == value", context));
    }

    private boolean matches(String expression, HookContext context) {
        return evaluator.matches(parser.parse(expression).orElseThrow(), context);
    }
}
