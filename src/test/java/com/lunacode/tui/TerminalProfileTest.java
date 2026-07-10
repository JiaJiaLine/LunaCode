package com.lunacode.tui;

import org.jline.terminal.Terminal;
import org.jline.utils.ColorPalette;
import org.jline.utils.InfoCmp;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalProfileTest {
    @Test
    void enablesModernTerminalCapabilities() {
        TerminalProfile profile = TerminalProfile.detect(
                terminal("xterm-256color", 256),
                Map.of("TERM", "xterm-256color"),
                StandardCharsets.UTF_8
        );

        assertTrue(profile.interactive());
        assertTrue(profile.ansiEnabled());
        assertTrue(profile.unicodeEnabled());
        assertEquals(256, profile.maxColors());
    }

    @Test
    void noColorDisablesAllDecorationEvenWhenValueIsEmpty() {
        TerminalProfile profile = TerminalProfile.detect(
                terminal("xterm-256color", 256),
                Map.of("NO_COLOR", ""),
                StandardCharsets.UTF_8
        );

        assertTrue(profile.interactive());
        assertFalse(profile.ansiEnabled());
        assertFalse(profile.unicodeEnabled());
    }

    @Test
    void dumbTerminalUsesPlainAscii() {
        TerminalProfile profile = TerminalProfile.detect(
                terminal("dumb", 8),
                Map.of(),
                StandardCharsets.UTF_8
        );

        assertTrue(profile.interactive());
        assertFalse(profile.ansiEnabled());
        assertFalse(profile.unicodeEnabled());
    }

    @Test
    void missingTerminalIsNonInteractive() {
        TerminalProfile profile = TerminalProfile.detect(
                null,
                Map.of("TERM", "xterm-256color"),
                StandardCharsets.UTF_8
        );

        assertFalse(profile.interactive());
        assertFalse(profile.ansiEnabled());
        assertFalse(profile.unicodeEnabled());
    }

    @Test
    void nonUnicodeCharsetFallsBackToAscii() {
        TerminalProfile profile = TerminalProfile.detect(
                terminal("xterm-256color", 256),
                Map.of(),
                StandardCharsets.US_ASCII
        );

        assertTrue(profile.ansiEnabled());
        assertFalse(profile.unicodeEnabled());
    }

    @Test
    void colorTermCanAdvertiseTrueColor() {
        TerminalProfile profile = TerminalProfile.detect(
                terminal("xterm", 8),
                Map.of("COLORTERM", "truecolor"),
                StandardCharsets.UTF_8
        );

        assertEquals(1 << 24, profile.maxColors());
    }

    private static Terminal terminal(String type, int maxColors) {
        return (Terminal) Proxy.newProxyInstance(
                TerminalProfileTest.class.getClassLoader(),
                new Class<?>[]{Terminal.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "getNumericCapability" -> arguments[0] == InfoCmp.Capability.max_colors
                            ? maxColors
                            : null;
                    case "encoding" -> StandardCharsets.UTF_8;
                    case "getPalette" -> ColorPalette.DEFAULT;
                    case "toString" -> "TestTerminal[" + type + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
