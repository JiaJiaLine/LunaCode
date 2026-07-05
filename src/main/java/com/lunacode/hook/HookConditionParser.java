package com.lunacode.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HookConditionParser {
    public Optional<HookCondition> parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return Optional.empty();
        }
        String raw = expression.strip();
        boolean hasAnd = raw.contains("&&");
        boolean hasOr = raw.contains("||");
        if (hasAnd && hasOr) {
            throw new IllegalArgumentException("条件表达式不能混用 && 和 ||: " + raw);
        }
        String separator = hasAnd ? "&&" : hasOr ? "||" : null;
        HookConditionMode mode = hasOr ? HookConditionMode.ANY : HookConditionMode.ALL;
        String[] parts = separator == null ? new String[]{raw} : raw.split(java.util.regex.Pattern.quote(separator));
        List<HookPredicate> predicates = new ArrayList<>();
        for (String part : parts) {
            String predicate = part.strip();
            if (predicate.isEmpty()) {
                throw new IllegalArgumentException("条件表达式包含空子条件: " + raw);
            }
            predicates.add(parsePredicate(predicate, raw));
        }
        return Optional.of(new HookCondition(mode, predicates, raw));
    }

    private HookPredicate parsePredicate(String predicate, String raw) {
        String[] pieces = predicate.split("\\s+", 3);
        if (pieces.length != 3) {
            throw new IllegalArgumentException("子条件必须是 field operator value 格式: " + predicate + " in " + raw);
        }
        HookOperator operator = HookOperator.fromSymbol(pieces[1])
                .orElseThrow(() -> new IllegalArgumentException("未知条件操作符: " + pieces[1] + " in " + raw));
        return new HookPredicate(pieces[0], operator, unquote(pieces[2].strip()));
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
