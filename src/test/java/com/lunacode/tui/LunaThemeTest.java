package com.lunacode.tui;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LunaThemeTest {
    private final LunaTheme theme = new LunaTheme();

    @Test
    void semanticTonesProduceColoredAndLocallyResetText() {
        TerminalProfile profile = new TerminalProfile(true, true, true, 256);

        for (TuiTone tone : EnumSet.complementOf(EnumSet.of(TuiTone.NORMAL))) {
            String styled = theme.style(tone, "text", null, profile);
            assertTrue(styled.contains("\u001B["), tone + " should contain ANSI styling");
            assertTrue(styled.endsWith("\u001B[0m"), tone + " should end with a local reset");
        }

        assertNotEquals(
                theme.style(TuiTone.BRAND_PRIMARY, "text", null, profile),
                theme.style(TuiTone.TOOL, "text", null, profile)
        );
    }

    @Test
    void disabledAnsiReturnsUnmodifiedPlainText() {
        TerminalProfile profile = new TerminalProfile(true, false, false, 256);

        String styled = theme.style(TuiTone.ERROR, "failure", null, profile);

        assertEquals("failure", styled);
        assertFalse(styled.contains("\u001B"));
        assertEquals("", theme.reset(profile));
    }

    @Test
    void everySymbolHasUnicodeAndAsciiRepresentations() {
        TerminalProfile unicode = new TerminalProfile(true, true, true, 256);
        TerminalProfile ascii = new TerminalProfile(true, false, false, 0);

        for (TuiSymbol symbol : TuiSymbol.values()) {
            String unicodeValue = theme.symbol(symbol, unicode);
            String asciiValue = theme.symbol(symbol, ascii);
            assertFalse(unicodeValue.isBlank());
            assertFalse(asciiValue.isBlank());
            assertTrue(asciiValue.chars().allMatch(value -> value < 128), symbol + " ASCII fallback");
        }

        assertEquals("✓", theme.symbol(TuiSymbol.SUCCESS, unicode));
        assertEquals("+", theme.symbol(TuiSymbol.SUCCESS, ascii));
        assertEquals("x", theme.symbol(TuiSymbol.FAILURE, ascii));
    }

    @Test
    void normalAndNullValuesDoNotIntroduceControlSequences() {
        TerminalProfile profile = new TerminalProfile(true, true, true, 256);

        assertEquals("plain", theme.style(TuiTone.NORMAL, "plain", null, profile));
        assertEquals("", theme.style(TuiTone.ERROR, null, null, profile));
        assertEquals("", theme.symbol(null, profile));
        assertEquals("\u001B[0m", theme.reset(profile));
    }
}
