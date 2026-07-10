package com.lunacode.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端底部可擦除区域的一帧内容。
 */
public record LiveRegionFrame(
        List<String> activityLines,
        List<String> completionLines,
        String contextLine,
        String promptLine,
        int cursorColumnsFromEnd
) {
    public LiveRegionFrame {
        activityLines = activityLines == null ? List.of() : List.copyOf(activityLines);
        completionLines = completionLines == null ? List.of() : List.copyOf(completionLines);
        contextLine = contextLine == null ? "" : contextLine;
        promptLine = promptLine == null ? "" : promptLine;
        cursorColumnsFromEnd = Math.max(0, cursorColumnsFromEnd);
    }

    public static LiveRegionFrame empty() {
        return new LiveRegionFrame(List.of(), List.of(), "", "", 0);
    }

    List<String> visibleLines() {
        List<String> lines = new ArrayList<>();
        appendVisible(lines, activityLines);
        appendVisible(lines, completionLines);
        if (!contextLine.isEmpty()) {
            lines.add(contextLine);
        }
        if (!promptLine.isEmpty()) {
            lines.add(promptLine);
        }
        return List.copyOf(lines);
    }

    private static void appendVisible(List<String> target, List<String> source) {
        for (String line : source) {
            if (line != null && !line.isEmpty()) {
                target.add(line);
            }
        }
    }
}
