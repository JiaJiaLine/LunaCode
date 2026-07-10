package com.lunacode.tui;

/**
 * 在彩色与纯文本终端中都能表达状态的语义符号。
 */
public enum TuiSymbol {
    BRAND_MOON("◐", "(L)"),
    USER("❯", ">"),
    ASSISTANT("✦", "*"),
    TOOL("●", "+"),
    SUCCESS("✓", "+"),
    FAILURE("✗", "x"),
    WARNING("⚠", "!"),
    QUESTION("?", "?"),
    PERMISSION("◇", "#"),
    TRUNCATE_LEFT("…", "<"),
    TRUNCATE_RIGHT("…", ">");

    private final String unicode;
    private final String ascii;

    TuiSymbol(String unicode, String ascii) {
        this.unicode = unicode;
        this.ascii = ascii;
    }

    String unicode() {
        return unicode;
    }

    String ascii() {
        return ascii;
    }
}
