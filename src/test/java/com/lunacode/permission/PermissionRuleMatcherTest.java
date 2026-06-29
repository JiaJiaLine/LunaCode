package com.lunacode.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionRuleMatcherTest {
    private final PermissionRuleMatcher matcher = new PermissionRuleMatcher();

    @Test
    void denyAcrossLayersCannotBeFlippedByLocalAllow() {
        LoadedPermissionRules rules = new LoadedPermissionRules(
                List.of(rule("Bash(rm *)", "Bash", "rm *", PermissionEffect.DENY, PermissionRuleLevel.USER, 1)),
                List.of(),
                List.of(rule("Bash(rm *)", "Bash", "rm *", PermissionEffect.ALLOW, PermissionRuleLevel.LOCAL, 1)),
                List.of()
        );

        assertTrue(matcher.matchDeny(rules, List.of(PermissionTarget.command("Bash", "rm something"))).isPresent());
    }

    @Test
    void allowUsesLocalProjectUserPriorityAndLaterRuleWinsWithinLayer() {
        LoadedPermissionRules rules = new LoadedPermissionRules(
                List.of(rule("Bash(git *)", "Bash", "git *", PermissionEffect.ALLOW, PermissionRuleLevel.USER, 1)),
                List.of(rule("Bash(git status*)", "Bash", "git status*", PermissionEffect.ALLOW, PermissionRuleLevel.PROJECT, 1)),
                List.of(
                        rule("Bash(git *)", "Bash", "git *", PermissionEffect.ALLOW, PermissionRuleLevel.LOCAL, 1),
                        rule("Bash(git status --short)", "Bash", "git status --short", PermissionEffect.ALLOW, PermissionRuleLevel.LOCAL, 2)
                ),
                List.of()
        );

        PermissionRuleMatch match = matcher.matchAllow(rules, List.of(PermissionTarget.command("Bash", "git status --short"))).orElseThrow();

        assertEquals(PermissionRuleLevel.LOCAL, match.rule().level());
        assertEquals(2, match.rule().order());
    }

    @Test
    void fileNameGlobMatchesVirtualPathBasename() {
        PermissionRule rule = rule("EditFile(*.py)", "EditFile", "*.py", PermissionEffect.ALLOW, PermissionRuleLevel.PROJECT, 1);
        LoadedPermissionRules rules = new LoadedPermissionRules(List.of(), List.of(rule), List.of(), List.of());

        assertTrue(matcher.matchAllow(rules, List.of(new PermissionTarget("EditFile", PermissionTargetKind.FILE_PATH, "/project/src/app.py", null, false))).isPresent());
    }

    private PermissionRule rule(String raw, String tool, String pattern, PermissionEffect effect, PermissionRuleLevel level, int order) {
        return new PermissionRule(raw, tool, pattern, effect, level, order, null);
    }
}
