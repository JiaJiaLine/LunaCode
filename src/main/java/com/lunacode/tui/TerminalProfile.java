package com.lunacode.tui;

import org.jline.terminal.Terminal;
import org.jline.terminal.spi.TerminalExt;
import org.jline.utils.AttributedCharSequence;
import org.jline.utils.InfoCmp;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Locale;
import java.util.Map;

/**
 * 当前终端可安全使用的展示能力快照。
 *
 * @param interactive   是否连接到可交互终端
 * @param ansiEnabled   是否允许输出 ANSI 颜色
 * @param unicodeEnabled 是否允许输出 Unicode 装饰符号
 * @param maxColors     终端声明的最大颜色数
 */
public record TerminalProfile(
        boolean interactive,
        boolean ansiEnabled,
        boolean unicodeEnabled,
        int maxColors
) {
    private static final String UNICODE_PROBE = "◐❯✦●✓✗⚠◇…";

    public TerminalProfile {
        maxColors = Math.max(0, maxColors);
    }

    /**
     * 从终端、环境变量与实际输出字符集推导展示能力。
     */
    public static TerminalProfile detect(
            Terminal terminal,
            Map<String, String> environment,
            Charset charset
    ) {
        Map<String, String> safeEnvironment = environment == null ? Map.of() : environment;
        String terminalType = terminalType(terminal, safeEnvironment);
        boolean interactive = isInteractive(terminal);
        boolean dumb = terminalType.toLowerCase(Locale.ROOT).startsWith(Terminal.TYPE_DUMB);
        boolean noColor = safeEnvironment.containsKey("NO_COLOR");
        int maxColors = detectMaxColors(terminal, safeEnvironment, terminalType);

        boolean decorationEnabled = interactive && !dumb && !noColor && maxColors > 0;
        Charset effectiveCharset = effectiveCharset(terminal, charset);
        boolean unicodeEnabled = decorationEnabled && canEncodeThemeSymbols(effectiveCharset);

        return new TerminalProfile(interactive, decorationEnabled, unicodeEnabled, maxColors);
    }

    private static boolean isInteractive(Terminal terminal) {
        if (terminal == null) {
            return false;
        }
        if (terminal instanceof TerminalExt extended) {
            return extended.getSystemStream() != null;
        }
        // 第三方或测试 Terminal 未必实现 TerminalExt；存在 Terminal 本身即视为可交互。
        return true;
    }

    private static String terminalType(Terminal terminal, Map<String, String> environment) {
        String type = terminal == null ? null : terminal.getType();
        if (type == null || type.isBlank()) {
            type = environment.get("TERM");
        }
        return type == null ? "" : type.trim();
    }

    private static int detectMaxColors(
            Terminal terminal,
            Map<String, String> environment,
            String terminalType
    ) {
        int colors = 0;
        if (terminal != null) {
            Integer declared = terminal.getNumericCapability(InfoCmp.Capability.max_colors);
            colors = declared == null ? 0 : Math.max(0, declared);
        }

        String colorTerm = environment.getOrDefault("COLORTERM", "").toLowerCase(Locale.ROOT);
        if (colorTerm.contains("truecolor") || colorTerm.contains("24bit")) {
            return Math.max(colors, AttributedCharSequence.TRUE_COLORS);
        }
        if (terminalType.toLowerCase(Locale.ROOT).contains("256color")) {
            return Math.max(colors, 256);
        }
        return colors;
    }

    private static Charset effectiveCharset(Terminal terminal, Charset charset) {
        if (charset != null) {
            return charset;
        }
        if (terminal != null && terminal.encoding() != null) {
            return terminal.encoding();
        }
        return Charset.defaultCharset();
    }

    private static boolean canEncodeThemeSymbols(Charset charset) {
        CharsetEncoder encoder = charset.newEncoder();
        return encoder.canEncode(UNICODE_PROBE);
    }
}
