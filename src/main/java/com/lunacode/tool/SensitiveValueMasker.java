package com.lunacode.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SensitiveValueMasker {
    private final List<String> values = new ArrayList<>();

    public SensitiveValueMasker() {
    }

    public SensitiveValueMasker(Collection<String> values) {
        if (values != null) {
            values.forEach(this::add);
        }
    }

    public void add(String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    public String mask(String text) {
        String result = text == null ? "" : text;
        for (String value : values) {
            result = result.replace(value, "[MASKED]");
        }
        return result;
    }
}
