package com.lunacode.tui;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveRegionRendererTest {
    private static final String ESC = "\u001B";

    @Test
    void renderDrawsFrameWithoutTrailingNewlineAndRestoresCursor() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);

        renderer.render(writer, new LiveRegionFrame(
                List.of("activity"),
                List.of("candidate"),
                "context",
                "> input",
                3
        ));

        String rendered = output.toString();
        assertTrue(rendered.startsWith("activity" + System.lineSeparator()));
        assertTrue(rendered.endsWith("> input" + ESC + "[3D"));
        assertEquals(4, renderer.renderedLines());
    }

    @Test
    void renderErasesEveryOldLineBeforeDrawingShorterFrame() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        renderer.render(writer, new LiveRegionFrame(
                List.of("a-long-running-activity"),
                List.of("one", "two"),
                "context",
                "> input",
                0
        ));
        int beforeSecondFrame = output.getBuffer().length();

        renderer.render(writer, new LiveRegionFrame(List.of(), List.of(), "", "> x", 0));

        String update = output.toString().substring(beforeSecondFrame);
        assertEquals(4, occurrences(update, ESC + "[1A"));
        assertEquals(5, occurrences(update, ESC + "[2K"));
        assertTrue(update.endsWith("> x"));
        assertFalse(update.endsWith(System.lineSeparator()));
        assertEquals(1, renderer.renderedLines());
    }

    @Test
    void beforePersistentOutputClearsLiveRegion() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        renderer.render(writer, new LiveRegionFrame(List.of("working"), List.of(), "", "> ", 0));
        int beforeClear = output.getBuffer().length();

        renderer.beforePersistentOutput(writer);
        writer.print("persistent");

        String update = output.toString().substring(beforeClear);
        assertTrue(update.startsWith("\r" + ESC + "[2K"));
        assertTrue(update.endsWith("persistent"));
        assertEquals(0, renderer.renderedLines());
    }

    @Test
    void emptyFrameClearsPreviousContentWithoutAddingHistory() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        renderer.render(writer, new LiveRegionFrame(List.of("working"), List.of(), "", "> ", 0));
        int beforeClear = output.getBuffer().length();

        renderer.render(writer, LiveRegionFrame.empty());

        String update = output.toString().substring(beforeClear);
        assertEquals("\r" + ESC + "[2K" + ESC + "[1A\r" + ESC + "[2K", update);
        assertEquals(0, renderer.renderedLines());
    }

    @Test
    void resetDropsCachedRowsAfterPhysicalScreenClear() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        renderer.render(writer, new LiveRegionFrame(List.of("working"), List.of(), "", "> ", 0));
        renderer.reset();
        int beforeRender = output.getBuffer().length();

        renderer.render(writer, new LiveRegionFrame(List.of(), List.of(), "", "> new", 0));

        String update = output.toString().substring(beforeRender);
        assertEquals("> new", update);
    }

    @Test
    void frameDefensivelyCopiesLineCollections() {
        java.util.ArrayList<String> activities = new java.util.ArrayList<>(List.of("one"));
        LiveRegionFrame frame = new LiveRegionFrame(activities, List.of(), "", "> ", 0);

        activities.add("two");

        assertEquals(List.of("one"), frame.activityLines());
    }

    @Test
    void resizeUsesPreviousVisualWidthToEraseWrappedRows() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);
        renderer.render(
                writer,
                new LiveRegionFrame(List.of("12345678901234567890"), List.of(), "", "", 0),
                40,
                true
        );
        output.getBuffer().setLength(0);

        renderer.clear(writer, 5, true);

        assertEquals(3, occurrences(output.toString(), ESC + "[1A"));
    }

    @Test
    void resizeMovesPastWrappedInputTailBeforeErasing() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);
        renderer.render(
                writer,
                new LiveRegionFrame(List.of(), List.of(), "", "12345678901234567890", 10),
                40,
                true
        );
        output.getBuffer().setLength(0);

        renderer.clear(writer, 5, true);

        assertTrue(output.toString().startsWith(ESC + "[1B\r"));
    }

    @Test
    void plainModeNeverWritesCursorControlSequences() {
        LiveRegionRenderer renderer = new LiveRegionRenderer();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);
        LiveRegionFrame frame = new LiveRegionFrame(
                List.of("| 正在处理"), List.of(), "model | mode:Agent", "> input", 0
        );

        renderer.render(writer, frame, 40, false);
        renderer.clear(writer, 40, false);

        assertFalse(output.toString().contains(ESC + "["));
        assertTrue(output.toString().contains("| 正在处理"));
        assertTrue(output.toString().contains("> input"));
    }

    private int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
