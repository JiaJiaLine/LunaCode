package com.lunacode.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookConditionParserTest {
    private final HookConditionParser parser = new HookConditionParser();

    @Test
    void parsesEmptySingleAndComposedConditions() {
        assertTrue(parser.parse(null).isEmpty());
        HookCondition single = parser.parse("tool == \"WriteFile\"").orElseThrow();
        assertEquals(HookConditionMode.ALL, single.mode());
        assertEquals("WriteFile", single.predicates().get(0).expected());

        HookCondition all = parser.parse("tool == WriteFile && args.path ~= *.java").orElseThrow();
        assertEquals(HookConditionMode.ALL, all.mode());
        assertEquals(2, all.predicates().size());

        HookCondition any = parser.parse("tool == WriteFile || tool == EditFile").orElseThrow();
        assertEquals(HookConditionMode.ANY, any.mode());
    }

    @Test
    void rejectsMixedOperatorsAndInvalidShape() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("A == B && C == D || E == F"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("tool WriteFile"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("tool <> WriteFile"));
    }
}
