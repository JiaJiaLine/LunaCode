package com.lunacode.permission;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PermissionRuleMatcher {
    public Optional<PermissionRuleMatch> matchDeny(LoadedPermissionRules rules, List<PermissionTarget> targets) {
        return rules.allRules().stream()
                .filter(rule -> rule.effect() == PermissionEffect.DENY)
                .flatMap(rule -> targets.stream()
                        .filter(target -> matches(rule, target))
                        .map(target -> toMatch(rule, target)))
                .max(matchComparator());
    }

    public Optional<PermissionRuleMatch> matchAllow(LoadedPermissionRules rules, List<PermissionTarget> targets) {
        for (PermissionRuleLevel level : List.of(PermissionRuleLevel.LOCAL, PermissionRuleLevel.PROJECT, PermissionRuleLevel.USER)) {
            Optional<PermissionRuleMatch> match = rules.rulesAt(level).stream()
                    .filter(rule -> rule.effect() == PermissionEffect.ALLOW)
                    .flatMap(rule -> targets.stream()
                            .filter(target -> matches(rule, target))
                            .map(target -> toMatch(rule, target)))
                    .max(Comparator.comparingInt(found -> found.rule().order()));
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private PermissionRuleMatch toMatch(PermissionRule rule, PermissionTarget target) {
        return new PermissionRuleMatch(rule, target, rule.effect().name().toLowerCase() + " 命中: " + rule.rawRule());
    }

    private boolean matches(PermissionRule rule, PermissionTarget target) {
        if (!rule.toolName().equals(target.toolName())) {
            return false;
        }
        String pattern = rule.pattern();
        String value = target.value();
        if (globMatches(pattern, value)) {
            return true;
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 && !pattern.contains("/") && !pattern.contains("\\")
                && globMatches(pattern, value.substring(slash + 1));
    }

    private boolean globMatches(String glob, String value) {
        return Pattern.compile(globToRegex(glob), Pattern.DOTALL).matcher(value).matches();
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '+', '$', '^', '|', '\\' -> regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        return regex.append('$').toString();
    }

    private Comparator<PermissionRuleMatch> matchComparator() {
        return Comparator
                .comparingInt((PermissionRuleMatch match) -> match.rule().level().priority())
                .thenComparingInt(match -> match.rule().order());
    }
}
