package com.lunacode.config;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvironmentValueExpander {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private final Map<String, String> environment;

    public EnvironmentValueExpander(Map<String, String> environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public String expand(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = environment.get(name);
            if (value == null || value.isBlank()) {
                throw new MissingEnvironmentValueException(name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public boolean containsPlaceholder(String raw) {
        return raw != null && PLACEHOLDER.matcher(raw).find();
    }

    public static final class MissingEnvironmentValueException extends IllegalArgumentException {
        private final String variableName;

        public MissingEnvironmentValueException(String variableName) {
            super("环境变量未设置或为空: " + variableName);
            this.variableName = variableName;
        }

        public String variableName() {
            return variableName;
        }
    }
}
