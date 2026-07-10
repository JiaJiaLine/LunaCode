package com.lunacode.tui;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpinnerTest {
    private final Spinner spinner = new Spinner();
    private final TerminalProfile unicode = new TerminalProfile(true, true, true, 256);
    private final TerminalProfile ascii = new TerminalProfile(true, false, false, 0);

    @Test
    void fixedElapsedTimesProduceDeterministicUnicodeFrames() {
        assertEquals("◐", spinner.frame(Duration.ZERO, unicode));
        assertEquals("◓", spinner.frame(Duration.ofMillis(100), unicode));
        assertEquals("◑", spinner.frame(Duration.ofMillis(200), unicode));
        assertEquals("◒", spinner.frame(Duration.ofMillis(300), unicode));
    }

    @Test
    void framesLoopWithoutSleeping() {
        assertEquals("◐", spinner.frame(Duration.ofMillis(400), unicode));
        assertEquals("◓", spinner.frame(Duration.ofSeconds(1).plusMillis(300), unicode));
        assertEquals("◑", spinner.frame(Duration.ofSeconds(Long.MAX_VALUE), unicode));
    }

    @Test
    void asciiProfileUsesPortableFrames() {
        assertEquals("|", spinner.frame(Duration.ZERO, ascii));
        assertEquals("/", spinner.frame(Duration.ofMillis(100), ascii));
        assertEquals("-", spinner.frame(Duration.ofMillis(200), ascii));
        assertEquals("\\", spinner.frame(Duration.ofMillis(300), ascii));
    }

    @Test
    void nullAndNegativeElapsedUseFirstFrame() {
        assertEquals("◐", spinner.frame(null, unicode));
        assertEquals("◐", spinner.frame(Duration.ofMillis(-1), unicode));
    }
}
