package com.lunacode.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentValueExpanderTest {
    @Test
    void expandsEmbeddedVariables() {
        EnvironmentValueExpander expander = new EnvironmentValueExpander(Map.of(
                "TOKEN", "abc123",
                "HOME", "/home/luna",
                "NAME", "cache"
        ));

        assertEquals("Bearer abc123", expander.expand("Bearer ${TOKEN}"));
        assertEquals("/home/luna/.cache/cache", expander.expand("${HOME}/.cache/${NAME}"));
        assertEquals("plain", expander.expand("plain"));
    }

    @Test
    void rejectsMissingOrEmptyVariables() {
        EnvironmentValueExpander expander = new EnvironmentValueExpander(Map.of("EMPTY", ""));

        assertThrows(EnvironmentValueExpander.MissingEnvironmentValueException.class, () -> expander.expand("${MISSING}"));
        assertThrows(EnvironmentValueExpander.MissingEnvironmentValueException.class, () -> expander.expand("${EMPTY}"));
    }
}
