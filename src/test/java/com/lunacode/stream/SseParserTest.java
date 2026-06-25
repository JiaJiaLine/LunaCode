package com.lunacode.stream;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SseParserTest {
    @Test
    void parsesEventAndData() {
        SseParser parser = new SseParser();

        List<SseEvent> events = parser.parseLines(List.of(
                "event: message_start",
                "data: {\"type\":\"message_start\"}",
                ""
        ));

        assertEquals(List.of(new SseEvent("message_start", "{\"type\":\"message_start\"}")), events);
    }

    @Test
    void joinsMultiLineData() {
        SseParser parser = new SseParser();

        List<SseEvent> events = parser.parseLines(List.of(
                "event: chunk",
                "data: first",
                "data: second",
                ""
        ));

        assertEquals("first\nsecond", events.get(0).data());
    }

    @Test
    void defaultsEventToMessageAndHandlesDone() {
        SseParser parser = new SseParser();

        List<SseEvent> events = parser.parseLines(List.of(
                "data: [DONE]",
                ""
        ));

        assertEquals(new SseEvent("message", "[DONE]"), events.get(0));
    }

    @Test
    void flushesFinalEventWithoutTrailingBlankLine() {
        SseParser parser = new SseParser();

        List<SseEvent> events = parser.parseLines(List.of(
                "event: message_stop",
                "data: {}"
        ));

        assertEquals(new SseEvent("message_stop", "{}"), events.get(0));
    }
}