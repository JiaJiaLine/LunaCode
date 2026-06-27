package com.lunacode.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputLineBufferTest {
    @Test
    void insertsAtCursorAfterMovingLeft() {
        InputLineBuffer buffer = new InputLineBuffer();

        buffer.insert('a');
        buffer.insert('b');
        buffer.insert('c');
        buffer.moveLeft();
        buffer.insert('X');

        assertEquals("abXc", buffer.content());
        assertEquals(3, buffer.cursorIndex());
        assertEquals(1, buffer.columnsAfterCursor());
    }

    @Test
    void backspaceAndDeleteRespectCursor() {
        InputLineBuffer buffer = new InputLineBuffer();

        buffer.insert('a');
        buffer.insert('b');
        buffer.insert('c');
        buffer.moveLeft();
        buffer.backspace();
        buffer.delete();

        assertEquals("a", buffer.content());
        assertEquals(1, buffer.cursorIndex());
    }

    @Test
    void consumeClearsContentAndCursor() {
        InputLineBuffer buffer = new InputLineBuffer();

        buffer.insert('你');
        buffer.insert('a');
        buffer.moveLeft();

        assertEquals(1, buffer.columnsAfterCursor());
        assertEquals("你a", buffer.consume());
        assertEquals("", buffer.content());
        assertEquals(0, buffer.cursorIndex());
    }
}