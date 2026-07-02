package com.lunacode.tui;

import org.jline.utils.WCWidth;

final class InputLineBuffer {
    private final StringBuilder content = new StringBuilder();
    private int cursorIndex;

    String content() {
        return content.toString();
    }

    boolean isEmpty() {
        return content.isEmpty();
    }

    int cursorIndex() {
        return cursorIndex;
    }

    void insert(int codePoint) {
        content.insert(cursorIndex, Character.toChars(codePoint));
        cursorIndex += Character.charCount(codePoint);
    }

    void backspace() {
        if (cursorIndex == 0) {
            return;
        }
        int previous = content.offsetByCodePoints(cursorIndex, -1);
        content.delete(previous, cursorIndex);
        cursorIndex = previous;
    }

    void delete() {
        if (cursorIndex >= content.length()) {
            return;
        }
        int next = content.offsetByCodePoints(cursorIndex, 1);
        content.delete(cursorIndex, next);
    }

    void moveLeft() {
        if (cursorIndex > 0) {
            cursorIndex = content.offsetByCodePoints(cursorIndex, -1);
        }
    }

    void moveRight() {
        if (cursorIndex < content.length()) {
            cursorIndex = content.offsetByCodePoints(cursorIndex, 1);
        }
    }

    void moveHome() {
        cursorIndex = 0;
    }

    void moveEnd() {
        cursorIndex = content.length();
    }


    void replaceCommandToken(String replacement) {
        String safeReplacement = replacement == null ? "" : replacement;
        int tokenEnd = 0;
        while (tokenEnd < content.length() && !Character.isWhitespace(content.charAt(tokenEnd))) {
            tokenEnd++;
        }
        content.replace(0, tokenEnd, safeReplacement);
        cursorIndex = safeReplacement.length();
    }

    void replaceAll(String value) {
        content.setLength(0);
        if (value != null && !value.isEmpty()) {
            content.append(value);
        }
        cursorIndex = content.length();
    }    String consume() {
        String value = content.toString();
        clear();
        return value;
    }

    void clear() {
        content.setLength(0);
        cursorIndex = 0;
    }

    int columnsAfterCursor() {
        int columns = 0;
        for (int i = cursorIndex; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int width = WCWidth.wcwidth(codePoint);
            columns += Math.max(width, 0);
            i += Character.charCount(codePoint);
        }
        return columns;
    }
}