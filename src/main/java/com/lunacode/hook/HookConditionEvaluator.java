package com.lunacode.hook;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class HookConditionEvaluator {
    public boolean matches(HookCondition condition, HookContext context) {
        if (condition == null) {
            return true;
        }
        HookContext safeContext = context == null ? HookContext.empty(null) : context;
        if (condition.mode() == HookConditionMode.ANY) {
            return condition.predicates().stream().anyMatch(predicate -> matchesPredicate(predicate, safeContext));
        }
        return condition.predicates().stream().allMatch(predicate -> matchesPredicate(predicate, safeContext));
    }

    private boolean matchesPredicate(HookPredicate predicate, HookContext context) {
        String actual = value(predicate.field(), context);
        String expected = predicate.expected();
        return switch (predicate.operator()) {
            case EQUALS -> actual.equals(expected);
            case NOT_EQUALS -> !actual.equals(expected);
            case REGEX -> regexMatches(actual, expected);
            case GLOB -> globMatches(actual, expected);
        };
    }

    private String value(String field, HookContext context) {
        if (field == null || field.isBlank()) {
            return "";
        }
        return switch (field) {
            case "eventName", "event" -> context.eventName();
            case "toolName", "tool" -> context.toolName();
            case "filePath", "path" -> context.filePath();
            case "message" -> context.message();
            case "error" -> context.error();
            default -> {
                if (field.startsWith("args.")) {
                    yield context.toolArgs().getOrDefault(field.substring("args.".length()), "");
                }
                if (field.startsWith("toolArgs.")) {
                    yield context.toolArgs().getOrDefault(field.substring("toolArgs.".length()), "");
                }
                yield "";
            }
        };
    }

    private boolean regexMatches(String actual, String expected) {
        try {
            return Pattern.compile(expected).matcher(actual).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private boolean globMatches(String actual, String glob) {
        return Pattern.compile(globToRegex(glob)).matcher(actual).matches();
    }

    private String globToRegex(String glob) {
        StringBuilder out = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> out.append(".*");
                case '?' -> out.append('.');
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> out.append('\\').append(c);
                default -> out.append(c);
            }
        }
        return out.append('$').toString();
    }
}
