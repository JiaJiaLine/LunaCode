package com.lunacode.session;

import java.util.Objects;
import java.util.regex.Pattern;

public record SessionId(String value) {
    private static final Pattern PATTERN = Pattern.compile("\\d{8}-\\d{6}-[0-9a-f]{4}");

    public SessionId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("会话 ID 格式无效: " + value);
        }
    }
}
