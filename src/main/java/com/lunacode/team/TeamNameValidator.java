package com.lunacode.team;

import java.util.regex.Pattern;

public final class TeamNameValidator {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public String validate(String value, String field) {
        String name = value == null ? "" : value.strip();
        if (name.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(field + " contains unsafe characters: " + value);
        }
        return name;
    }
}
