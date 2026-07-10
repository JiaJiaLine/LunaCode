package com.lunacode.tui;

/**
 * 一次输入区域布局的纯数据快照。
 *
 * @param contextLine 紧凑上下文行，空字符串表示不显示
 * @param promptLine 已应用水平视口的输入行
 * @param cursorColumnsFromEnd 绘制到行尾后需要向左回退的显示列数
 */
public record PromptFrame(
        String contextLine,
        String promptLine,
        int cursorColumnsFromEnd
) {
    public PromptFrame {
        contextLine = contextLine == null ? "" : contextLine;
        promptLine = promptLine == null ? "" : promptLine;
        cursorColumnsFromEnd = Math.max(0, cursorColumnsFromEnd);
    }
}
