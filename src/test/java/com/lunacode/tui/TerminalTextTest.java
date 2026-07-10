package com.lunacode.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TerminalTextTest {
    @Test
    void escapesTerminalControlsButKeepsNormalUnicodeAndLines() {
        String value = "第一行\u001B]0;hacked\u0007\n第二行\t🙂";

        String safe = TerminalText.multiline(value);

        assertEquals("第一行\\x1b]0;hacked\\x07\n第二行    🙂", safe);
        assertFalse(safe.contains("\u001B"));
        assertFalse(safe.contains("\u0007"));
    }

    @Test
    void singleLineNeutralizesCarriageReturnAndNewline() {
        assertEquals("before after tail", TerminalText.singleLine("before\rafter\ntail"));
    }
}
