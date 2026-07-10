package com.lunacode.tui;

import com.lunacode.command.SlashCommandName;
import org.jline.utils.WCWidth;

import java.util.ArrayList;
import java.util.List;

/**
 * 按终端显示列计算文本布局，避免使用 UTF-16 字符数量定位光标。
 */
public final class TerminalLayout {
    private static final String ASCII_PROMPT = "> ";
    private static final String UNICODE_PROMPT = "❯ ";
    private static final String COMPLETION_PREFIX = "候选: ";
    private static final String COMPLETION_CONTINUATION = "  ";
    private static final String CANDIDATE_SEPARATOR = "  ";

    /**
     * 返回文本占用的终端显示列数。控制字符和组合字符不增加列宽。
     */
    public int columns(String text) {
        String value = safe(text);
        int columns = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            columns += Math.max(0, WCWidth.wcwidth(codePoint));
            index += Character.charCount(codePoint);
        }
        return columns;
    }

    /**
     * 从尾部截断文本，并确保结果不超过 maxColumns。
     */
    public String truncate(String text, int maxColumns, String ellipsis) {
        String value = safe(text);
        int width = Math.max(0, maxColumns);
        if (width == 0 || value.isEmpty()) {
            return "";
        }
        if (columns(value) <= width) {
            return value;
        }

        String marker = safe(ellipsis);
        int markerWidth = columns(marker);
        if (markerWidth >= width) {
            return prefix(marker, width);
        }
        return prefix(value, width - markerWidth) + marker;
    }

    /**
     * 从开头截断文本，供水平输入视口和路径等尾部优先内容复用。
     */
    public String truncateLeft(String text, int maxColumns, String ellipsis) {
        String value = safe(text);
        int width = Math.max(0, maxColumns);
        if (width == 0 || value.isEmpty()) {
            return "";
        }
        if (columns(value) <= width) {
            return value;
        }

        String marker = safe(ellipsis);
        int markerWidth = columns(marker);
        if (markerWidth >= width) {
            return prefix(marker, width);
        }
        return marker + suffix(value, width - markerWidth);
    }

    /**
     * 构造固定顺序的紧凑状态，空字段会被省略。
     */
    public String compactContext(String model, String agentMode, String permissionMode, String sessionId) {
        List<String> parts = new ArrayList<>(4);
        addPart(parts, model);
        addLabeledPart(parts, "mode", agentMode);
        addLabeledPart(parts, "perm", permissionMode);
        addLabeledPart(parts, "s", sessionId);
        return String.join(" · ", parts);
    }

    /**
     * 生成紧凑状态行和围绕光标的输入水平视口。
     */
    public PromptFrame prompt(
            String compactContext,
            String input,
            int cursorIndex,
            int terminalWidth,
            TerminalProfile profile
    ) {
        int width = Math.max(1, terminalWidth);
        boolean unicode = profile != null && profile.unicodeEnabled();
        String ellipsis = unicode ? "…" : "...";
        String leftMarker = unicode ? "‹" : "<";
        String rightMarker = unicode ? "›" : ">";

        String context = truncate(safe(compactContext), width, ellipsis);
        String value = safe(input);
        int cursor = safeCursorBoundary(value, cursorIndex);

        String requestedPrompt = unicode ? UNICODE_PROMPT : ASCII_PROMPT;
        String promptPrefix = columns(requestedPrompt) <= width
                ? requestedPrompt
                : prefix(requestedPrompt, width);
        int inputBudget = Math.max(0, width - columns(promptPrefix));
        Viewport viewport = viewport(value, cursor, inputBudget, leftMarker, rightMarker);
        return new PromptFrame(
                context,
                promptPrefix + viewport.text(),
                viewport.columnsAfterCursor()
        );
    }

    /**
     * 按终端宽度排列命令候选。命令本身优先于别名归属信息保留。
     */
    public List<String> completionLines(List<SlashCommandName> candidates, int terminalWidth) {
        return completionLines(candidates, terminalWidth, null);
    }

    /**
     * 根据终端能力选择 Unicode 或 ASCII 省略符的候选布局。
     */
    public List<String> completionLines(
            List<SlashCommandName> candidates,
            int terminalWidth,
            TerminalProfile profile
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int width = Math.max(1, terminalWidth);
        String ellipsis = profile != null && profile.unicodeEnabled() ? "…" : "...";
        String firstPrefix = columns(COMPLETION_PREFIX) < width ? COMPLETION_PREFIX : "";
        String continuation = columns(COMPLETION_CONTINUATION) < width ? COMPLETION_CONTINUATION : "";

        List<String> lines = new ArrayList<>();
        String prefix = firstPrefix;
        StringBuilder line = new StringBuilder(prefix);
        boolean hasCandidate = false;

        for (SlashCommandName candidate : candidates) {
            if (candidate == null || isBlank(candidate.value())) {
                continue;
            }
            String separator = hasCandidate ? CANDIDATE_SEPARATOR : "";
            int available = width - columns(line.toString()) - columns(separator);
            String segment = fitCandidate(candidate, available, ellipsis);

            if (segment.isEmpty() && hasCandidate) {
                lines.add(line.toString());
                prefix = continuation;
                line = new StringBuilder(prefix);
                hasCandidate = false;
                available = width - columns(line.toString());
                segment = fitCandidate(candidate, available, ellipsis);
            } else if (hasCandidate && columns(candidateText(candidate)) > available) {
                lines.add(line.toString());
                prefix = continuation;
                line = new StringBuilder(prefix);
                hasCandidate = false;
                separator = "";
                available = width - columns(line.toString());
                segment = fitCandidate(candidate, available, ellipsis);
            }

            if (!segment.isEmpty()) {
                if (hasCandidate) {
                    line.append(CANDIDATE_SEPARATOR);
                }
                line.append(segment);
                hasCandidate = true;
            }
        }

        if (hasCandidate) {
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    private Viewport viewport(
            String value,
            int cursor,
            int budget,
            String leftMarker,
            String rightMarker
    ) {
        if (budget <= 0 || value.isEmpty()) {
            return new Viewport("", 0);
        }
        if (columns(value) <= budget) {
            return new Viewport(value, columns(value.substring(cursor)));
        }

        List<TextUnit> units = units(value);
        int cursorUnit = unitBoundaryAt(units, cursor);
        int start = cursorUnit;
        int end = cursorUnit;
        boolean preferLeft = cursorUnit >= units.size() - cursorUnit;

        while (true) {
            boolean grew = false;
            if (preferLeft) {
                if (start > 0 && viewportWidth(units, start - 1, end, leftMarker, rightMarker) <= budget) {
                    start--;
                    grew = true;
                } else if (end < units.size() && viewportWidth(units, start, end + 1, leftMarker, rightMarker) <= budget) {
                    end++;
                    grew = true;
                }
            } else {
                if (end < units.size() && viewportWidth(units, start, end + 1, leftMarker, rightMarker) <= budget) {
                    end++;
                    grew = true;
                } else if (start > 0 && viewportWidth(units, start - 1, end, leftMarker, rightMarker) <= budget) {
                    start--;
                    grew = true;
                }
            }
            if (!grew) {
                break;
            }
            preferLeft = !preferLeft;
        }

        boolean hiddenLeft = start > 0;
        boolean hiddenRight = end < units.size();
        StringBuilder visible = new StringBuilder();
        if (hiddenLeft) {
            visible.append(leftMarker);
        }
        for (int index = start; index < end; index++) {
            visible.append(units.get(index).text());
        }
        if (hiddenRight) {
            visible.append(rightMarker);
        }

        // 极窄终端可能连两个边界符都放不下，优先显示光标所在方向的边界。
        String rendered = visible.toString();
        if (columns(rendered) > budget) {
            rendered = cursor == 0
                    ? prefix(rightMarker, budget)
                    : prefix(leftMarker, budget);
            return new Viewport(rendered, cursor == 0 ? columns(rendered) : 0);
        }

        int afterCursor = 0;
        for (int index = cursorUnit; index < end; index++) {
            afterCursor += units.get(index).width();
        }
        if (hiddenRight) {
            afterCursor += columns(rightMarker);
        }
        return new Viewport(rendered, afterCursor);
    }

    private int viewportWidth(
            List<TextUnit> units,
            int start,
            int end,
            String leftMarker,
            String rightMarker
    ) {
        int width = start > 0 ? columns(leftMarker) : 0;
        for (int index = start; index < end; index++) {
            width += units.get(index).width();
        }
        if (end < units.size()) {
            width += columns(rightMarker);
        }
        return width;
    }

    private List<TextUnit> units(String value) {
        List<TextUnit> units = new ArrayList<>();
        for (int index = 0; index < value.length(); ) {
            int start = index;
            int codePoint = value.codePointAt(index);
            int width = Math.max(0, WCWidth.wcwidth(codePoint));
            index += Character.charCount(codePoint);

            // 将后续零宽字符附着到当前单元，避免截断后留下孤立组合符。
            while (index < value.length()) {
                int next = value.codePointAt(index);
                int nextWidth = Math.max(0, WCWidth.wcwidth(next));
                if (nextWidth != 0) {
                    break;
                }
                index += Character.charCount(next);
            }
            units.add(new TextUnit(value.substring(start, index), width, start, index));
        }
        return units;
    }

    private int unitBoundaryAt(List<TextUnit> units, int cursor) {
        for (int index = 0; index < units.size(); index++) {
            TextUnit unit = units.get(index);
            if (cursor <= unit.start()) {
                return index;
            }
            if (cursor < unit.end()) {
                return index + 1;
            }
        }
        return units.size();
    }

    private String fitCandidate(SlashCommandName candidate, int maxColumns, String ellipsis) {
        if (maxColumns <= 0) {
            return "";
        }
        String command = safe(candidate.value());
        String owner = safe(candidate.ownerCommand());
        if (owner.isBlank() || owner.equals(command)) {
            return truncate(command, maxColumns, ellipsis);
        }

        String attribution = " -> " + owner;
        if (columns(command + attribution) <= maxColumns) {
            return command + attribution;
        }
        if (columns(command) >= maxColumns) {
            return truncate(command, maxColumns, ellipsis);
        }
        int attributionBudget = maxColumns - columns(command);
        return command + truncate(attribution, attributionBudget, ellipsis);
    }

    private String candidateText(SlashCommandName candidate) {
        String command = safe(candidate.value());
        String owner = safe(candidate.ownerCommand());
        return owner.isBlank() || owner.equals(command) ? command : command + " -> " + owner;
    }

    private String prefix(String value, int maxColumns) {
        if (maxColumns <= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (TextUnit unit : units(value)) {
            if (unit.width() > 0 && used + unit.width() > maxColumns) {
                break;
            }
            result.append(unit.text());
            used += unit.width();
        }
        return result.toString();
    }

    private String suffix(String value, int maxColumns) {
        if (maxColumns <= 0) {
            return "";
        }
        List<TextUnit> units = units(value);
        int start = units.size();
        int used = 0;
        while (start > 0) {
            TextUnit unit = units.get(start - 1);
            if (unit.width() > 0 && used + unit.width() > maxColumns) {
                break;
            }
            start--;
            used += unit.width();
        }
        StringBuilder result = new StringBuilder();
        for (int index = start; index < units.size(); index++) {
            result.append(units.get(index).text());
        }
        return result.toString();
    }

    private int safeCursorBoundary(String value, int requested) {
        int cursor = Math.max(0, Math.min(requested, value.length()));
        if (cursor > 0
                && cursor < value.length()
                && Character.isLowSurrogate(value.charAt(cursor))
                && Character.isHighSurrogate(value.charAt(cursor - 1))) {
            return cursor - 1;
        }
        return cursor;
    }

    private void addPart(List<String> parts, String value) {
        if (!isBlank(value)) {
            parts.add(value.strip());
        }
    }

    private void addLabeledPart(List<String> parts, String label, String value) {
        if (!isBlank(value)) {
            parts.add(label + ":" + value.strip());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record TextUnit(String text, int width, int start, int end) {
    }

    private record Viewport(String text, int columnsAfterCursor) {
    }
}
