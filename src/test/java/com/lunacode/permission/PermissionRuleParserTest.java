package com.lunacode.permission;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionRuleParserTest {
    private final PermissionRuleParser parser = new PermissionRuleParser();

    @Test
    void parsesToolPatternAndEffect() {
        PermissionRule rule = parser.parse("Bash(git *)", "allow", PermissionRuleLevel.PROJECT, 1, Path.of("permissions.yaml"));

        assertEquals("Bash", rule.toolName());
        assertEquals("git *", rule.pattern());
        assertEquals(PermissionEffect.ALLOW, rule.effect());
    }

    @Test
    void unescapesLegacyParenthesesInPattern() {
        PermissionRule rule = parser.parse("Bash(echo \\))", "allow", PermissionRuleLevel.LOCAL, 1, Path.of("permissions.yaml"));

        assertEquals("echo )", rule.pattern());
    }

    @Test
    void rejectsInvalidRuleFormat() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("Bash git *", "allow", PermissionRuleLevel.USER, 1, null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("(git *)", "allow", PermissionRuleLevel.USER, 1, null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("Bash()", "allow", PermissionRuleLevel.USER, 1, null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("Bash(git *)", "maybe", PermissionRuleLevel.USER, 1, null));
    }
}
