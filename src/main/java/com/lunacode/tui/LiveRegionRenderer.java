package com.lunacode.tui;

import org.jline.utils.WCWidth;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 只负责终端底部实时区域的原位擦除与重绘。
 */
public final class LiveRegionRenderer {
    private static final String ESC = "\u001B";
    private static final String ERASE_LINE = ESC + "[2K";
    private static final String CURSOR_UP = ESC + "[1A";
    private static final String CURSOR_DOWN = ESC + "[%dB";
    private static final Pattern ANSI_CSI = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");

    private int renderedLines;
    private int lastTerminalWidth = 80;
    private List<Integer> renderedLineWidths = List.of();
    private List<String> lastPlainLines = List.of();
    private int cursorColumnsFromEnd;

    public synchronized void beforePersistentOutput(PrintWriter writer) {
        beforePersistentOutput(writer, lastTerminalWidth, true);
    }

    public synchronized void beforePersistentOutput(
            PrintWriter writer,
            int terminalWidth,
            boolean cursorControlEnabled
    ) {
        clear(writer, terminalWidth, cursorControlEnabled);
    }

    public synchronized void render(PrintWriter writer, LiveRegionFrame frame) {
        render(writer, frame, lastTerminalWidth, true);
    }

    public synchronized void render(
            PrintWriter writer,
            LiveRegionFrame frame,
            int terminalWidth,
            boolean cursorControlEnabled
    ) {
        Objects.requireNonNull(writer, "writer");
        LiveRegionFrame safeFrame = frame == null ? LiveRegionFrame.empty() : frame;
        int width = Math.max(1, terminalWidth);
        lastTerminalWidth = width;

        if (!cursorControlEnabled) {
            renderPlain(writer, safeFrame);
            return;
        }

        erasePrevious(writer, width);
        List<String> lines = safeFrame.visibleLines();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                writer.print(System.lineSeparator());
            }
            writer.print(lines.get(index));
        }
        renderedLines = lines.size();
        renderedLineWidths = lineWidths(lines);
        lastPlainLines = List.of();
        cursorColumnsFromEnd = safeFrame.cursorColumnsFromEnd();

        if (renderedLines > 0 && safeFrame.cursorColumnsFromEnd() > 0) {
            writer.print(ESC + "[" + safeFrame.cursorColumnsFromEnd() + "D");
        }
        writer.flush();
    }

    public synchronized void clear(PrintWriter writer) {
        clear(writer, lastTerminalWidth, true);
    }

    public synchronized void clear(
            PrintWriter writer,
            int terminalWidth,
            boolean cursorControlEnabled
    ) {
        Objects.requireNonNull(writer, "writer");
        int width = Math.max(1, terminalWidth);
        lastTerminalWidth = width;
        if (cursorControlEnabled) {
            erasePrevious(writer, width);
            writer.flush();
        } else {
            resetState();
        }
    }

    public synchronized void reset() {
        resetState();
    }

    int renderedLines() {
        return renderedLines;
    }

    private void renderPlain(PrintWriter writer, LiveRegionFrame frame) {
        List<String> lines = frame.visibleLines();
        if (lines.equals(lastPlainLines)) {
            return;
        }
        for (String line : lines) {
            writer.println(line);
        }
        renderedLines = lines.size();
        renderedLineWidths = lineWidths(lines);
        lastPlainLines = List.copyOf(lines);
        cursorColumnsFromEnd = 0;
        writer.flush();
    }

    private void erasePrevious(PrintWriter writer, int terminalWidth) {
        if (renderedLines == 0) {
            resetState();
            return;
        }
        int physicalRows = physicalRows(renderedLineWidths, terminalWidth);
        int rowsBelowCursor = rowsBelowCursor(terminalWidth);
        if (rowsBelowCursor > 0) {
            writer.print(CURSOR_DOWN.formatted(rowsBelowCursor));
        }
        writer.print('\r');
        writer.print(ERASE_LINE);
        for (int index = 1; index < physicalRows; index++) {
            writer.print(CURSOR_UP);
            writer.print('\r');
            writer.print(ERASE_LINE);
        }
        resetState();
    }

    private int physicalRows(List<Integer> widths, int terminalWidth) {
        int width = Math.max(1, terminalWidth);
        int rows = 0;
        for (Integer value : widths) {
            int lineWidth = value == null ? 0 : Math.max(0, value);
            rows += Math.max(1, (lineWidth + width - 1) / width);
        }
        return Math.max(rows, renderedLines);
    }

    private List<Integer> lineWidths(List<String> lines) {
        List<Integer> widths = new ArrayList<>(lines.size());
        for (String line : lines) {
            widths.add(visibleColumns(line));
        }
        return List.copyOf(widths);
    }

    private int rowsBelowCursor(int terminalWidth) {
        if (renderedLineWidths.isEmpty() || cursorColumnsFromEnd <= 0) {
            return 0;
        }
        int width = Math.max(1, terminalWidth);
        int lastLineWidth = renderedLineWidths.get(renderedLineWidths.size() - 1);
        int cursorColumn = Math.max(0, lastLineWidth - cursorColumnsFromEnd);
        int endColumn = Math.max(0, lastLineWidth - 1);
        return Math.max(0, endColumn / width - cursorColumn / width);
    }

    private int visibleColumns(String text) {
        String plain = ANSI_CSI.matcher(text == null ? "" : text).replaceAll("");
        int columns = 0;
        for (int index = 0; index < plain.length(); ) {
            int codePoint = plain.codePointAt(index);
            columns += Math.max(0, WCWidth.wcwidth(codePoint));
            index += Character.charCount(codePoint);
        }
        return columns;
    }

    private void resetState() {
        renderedLines = 0;
        renderedLineWidths = List.of();
        lastPlainLines = List.of();
        cursorColumnsFromEnd = 0;
    }
}
