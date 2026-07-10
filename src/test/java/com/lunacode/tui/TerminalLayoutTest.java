package com.lunacode.tui;

import com.lunacode.command.SlashCommandName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalLayoutTest {
    private final TerminalLayout layout = new TerminalLayout();
    private final TerminalProfile unicode = new TerminalProfile(true, true, true, 256);
    private final TerminalProfile ascii = new TerminalProfile(true, false, false, 0);

    @Test
    void truncateAsciiUsesRequestedColumns() {
        assertEquals("ab...", layout.truncate("abcdef", 5, "..."));
        assertEquals("abcdef", layout.truncate("abcdef", 6, "..."));
    }

    @Test
    void truncateChineseAndEmojiByDisplayWidth() {
        assertEquals("你好…", layout.truncate("你好世界", 5, "…"));
        assertEquals("ab🙂…", layout.truncate("ab🙂cd", 5, "…"));
    }

    @Test
    void truncateKeepsCombiningMarkWithBaseCharacter() {
        assertEquals("e\u0301c…", layout.truncate("e\u0301clair", 3, "…"));
        assertEquals(3, layout.columns(layout.truncate("e\u0301clair", 3, "…")));
    }

    @Test
    void truncateNeverSplitsSurrogatePair() {
        String result = layout.truncate("🙂🙂🙂", 3, "…");

        assertEquals("🙂…", result);
        assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 1)));
        assertFalse(Character.isLowSurrogate(result.charAt(0)));
    }

    @Test
    void truncateHandlesWidthSmallerThanEllipsis() {
        assertEquals("..", layout.truncate("abcdef", 2, "..."));
        assertEquals("", layout.truncate("abcdef", 0, "..."));
        assertEquals("…", layout.truncate("abcdef", 1, "…"));
    }

    @Test
    void truncateLeftKeepsTailWithinDisplayWidth() {
        assertEquals("…世界", layout.truncateLeft("你好世界", 5, "…"));
        assertEquals("...ef", layout.truncateLeft("abcdef", 5, "..."));
    }

    @Test
    void promptKeepsFullInputAndRestoresMiddleCursor() {
        PromptFrame frame = layout.prompt("model · mode:default", "abcd", 2, 24, unicode);

        assertEquals("model · mode:default", frame.contextLine());
        assertEquals("❯ abcd", frame.promptLine());
        assertEquals(2, frame.cursorColumnsFromEnd());
    }

    @Test
    void promptUsesHorizontalWindowAroundCursor() {
        PromptFrame frame = layout.prompt("very-long-model-name", "abcdefghij", 5, 8, unicode);

        assertEquals(8, layout.columns(frame.promptLine()));
        assertTrue(frame.promptLine().contains("‹"));
        assertTrue(frame.promptLine().contains("›"));
        assertEquals(3, frame.cursorColumnsFromEnd());
        assertTrue(layout.columns(frame.contextLine()) <= 8);
    }

    @Test
    void promptKeepsCursorAtBeginningAndEndVisible() {
        PromptFrame beginning = layout.prompt("", "abcdefghij", 0, 8, unicode);
        PromptFrame end = layout.prompt("", "abcdefghij", 10, 8, unicode);

        assertTrue(beginning.promptLine().endsWith("›"));
        assertEquals(6, beginning.cursorColumnsFromEnd());
        assertTrue(end.promptLine().contains("‹"));
        assertEquals(0, end.cursorColumnsFromEnd());
    }

    @Test
    void promptUsesAsciiMarkersAndHandlesNarrowTerminal() {
        PromptFrame viewport = layout.prompt("long-context", "abcdefghij", 5, 7, ascii);
        PromptFrame narrow = layout.prompt("context", "中文🙂", 1, 1, ascii);

        assertTrue(viewport.promptLine().contains("<"));
        assertTrue(viewport.promptLine().contains(">"));
        assertEquals(">", narrow.promptLine());
        assertEquals(0, narrow.cursorColumnsFromEnd());
    }

    @Test
    void promptHandlesLongChineseInputWithoutOverflow() {
        PromptFrame frame = layout.prompt("模型非常长", "甲乙丙丁戊己庚辛", 4, 10, unicode);

        assertTrue(layout.columns(frame.promptLine()) <= 10);
        assertTrue(layout.columns(frame.contextLine()) <= 10);
        assertTrue(frame.cursorColumnsFromEnd() <= 8);
    }

    @Test
    void compactContextUsesStableOrderAndOmitsMissingFields() {
        assertEquals(
                "gpt-test · mode:plan · perm:ask · s:abc123",
                layout.compactContext("gpt-test", "plan", "ask", "abc123")
        );
        assertEquals("gpt-test · perm:ask", layout.compactContext("gpt-test", null, "ask", " "));
    }

    @Test
    void completionReturnsEmptyForNoCandidates() {
        assertEquals(List.of(), layout.completionLines(List.of(), 80));
        assertEquals(List.of(), layout.completionLines(null, 80));
    }

    @Test
    void completionPacksCandidatesWhenTheyFit() {
        List<String> lines = layout.completionLines(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/status", "/status")
        ), 32);

        assertEquals(List.of("候选: /plan  /status"), lines);
    }

    @Test
    void completionShowsAliasOwnership() {
        List<String> lines = layout.completionLines(List.of(
                new SlashCommandName("/p", "/permission")
        ), 32);

        assertEquals(List.of("候选: /p -> /permission"), lines);
    }

    @Test
    void completionWrapsAtTerminalWidth() {
        List<String> lines = layout.completionLines(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/permission", "/permission"),
                new SlashCommandName("/status", "/status")
        ), 16);

        assertTrue(lines.size() >= 2);
        assertTrue(lines.stream().allMatch(line -> layout.columns(line) <= 16));
        assertTrue(String.join("\n", lines).contains("/plan"));
        assertTrue(String.join("\n", lines).contains("/permission"));
    }

    @Test
    void completionTruncatesLongAliasButKeepsCommand() {
        List<String> lines = layout.completionLines(List.of(
                new SlashCommandName("/p", "/permission-with-a-very-long-owner")
        ), 14);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("/p"));
        assertTrue(layout.columns(lines.get(0)) <= 14);
        assertFalse(lines.get(0).chars().anyMatch(value -> Character.isSurrogate((char) value)));
    }
}
