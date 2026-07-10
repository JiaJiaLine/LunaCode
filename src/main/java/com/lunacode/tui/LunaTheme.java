package com.lunacode.tui;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

/**
 * Luna 月夜主题。所有调用均返回自包含样式片段，避免颜色泄漏到后续输出。
 */
public final class LunaTheme {
    private static final String ANSI_RESET = "\u001B[0m";

    /**
     * 为文本应用语义样式；无颜色能力时返回原始纯文本。
     */
    public String style(TuiTone tone, String text, Terminal terminal, TerminalProfile profile) {
        String safeText = text == null ? "" : text;
        TerminalProfile safeProfile = profile == null
                ? new TerminalProfile(false, false, false, 0)
                : profile;
        TuiTone safeTone = tone == null ? TuiTone.NORMAL : tone;
        if (!safeProfile.ansiEnabled() || safeText.isEmpty() || safeTone == TuiTone.NORMAL) {
            return safeText;
        }

        AttributedStringBuilder builder = new AttributedStringBuilder();
        builder.styled(styleFor(safeTone), safeText);
        int maxColors = colorCount(terminal, safeProfile);
        String styled = builder.toAnsi(maxColors, true);
        return styled.endsWith(ANSI_RESET) ? styled : styled + ANSI_RESET;
    }

    /**
     * 根据能力快照选择 Unicode 或 ASCII 符号。
     */
    public String symbol(TuiSymbol symbol, TerminalProfile profile) {
        if (symbol == null) {
            return "";
        }
        return profile != null && profile.unicodeEnabled() ? symbol.unicode() : symbol.ascii();
    }

    /**
     * 返回关闭所有 ANSI 样式的控制序列。
     */
    public String reset(TerminalProfile profile) {
        return profile != null && profile.ansiEnabled() ? ANSI_RESET : "";
    }

    private static int colorCount(Terminal terminal, TerminalProfile profile) {
        if (profile.maxColors() > 0) {
            return profile.maxColors();
        }
        if (terminal != null) {
            Integer colors = terminal.getNumericCapability(InfoCmp.Capability.max_colors);
            if (colors != null && colors > 0) {
                return colors;
            }
        }
        return 8;
    }

    private static AttributedStyle styleFor(TuiTone tone) {
        return switch (tone) {
            case BRAND_PRIMARY, USER -> rgb(0xA78BFA).bold();
            case BRAND_SECONDARY, ASSISTANT -> rgb(0x60A5FA).bold();
            case TOOL -> rgb(0x22D3EE);
            case SUCCESS -> rgb(0x4ADE80);
            case WARNING -> rgb(0xFACC15);
            case ERROR -> rgb(0xF87171);
            case MUTED -> rgb(0x6B7280).faint();
            case NORMAL -> AttributedStyle.DEFAULT;
        };
    }

    private static AttributedStyle rgb(int color) {
        return AttributedStyle.DEFAULT.foregroundRgb(color);
    }
}
