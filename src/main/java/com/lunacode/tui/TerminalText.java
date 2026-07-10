package com.lunacode.tui;

/**
 * 把模型、工具和用户提供的文本转换为不会控制终端的可见文本。
 */
final class TerminalText {
    private TerminalText() {
    }

    static String multiline(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder safe = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '\n') {
                safe.append('\n');
            } else if (codePoint == '\r') {
                if (index >= value.length() || value.charAt(index) != '\n') {
                    safe.append('\n');
                }
            } else if (codePoint == '\t') {
                safe.append("    ");
            } else if (Character.isISOControl(codePoint)
                    || (codePoint >= 0x80 && codePoint <= 0x9F)) {
                appendEscapedControl(safe, codePoint);
            } else {
                safe.appendCodePoint(codePoint);
            }
        }
        return safe.toString();
    }

    static String singleLine(String value) {
        return multiline(value).replace('\n', ' ');
    }

    private static void appendEscapedControl(StringBuilder target, int codePoint) {
        if (codePoint <= 0xFF) {
            target.append("\\x");
            String hex = Integer.toHexString(codePoint);
            if (hex.length() == 1) {
                target.append('0');
            }
            target.append(hex);
        } else {
            target.append('�');
        }
    }
}
