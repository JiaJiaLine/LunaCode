package com.lunacode.tui;

import java.time.Duration;

/**
 * 只根据已用时间计算帧的无状态加载动画。
 */
public final class Spinner {
    static final Duration FRAME_PERIOD = Duration.ofMillis(100);

    private static final String[] UNICODE_FRAMES = {"◐", "◓", "◑", "◒"};
    private static final String[] ASCII_FRAMES = {"|", "/", "-", "\\"};
    private static final int FRAMES_PER_SECOND = 10;

    public String frame(Duration elapsed, TerminalProfile profile) {
        String[] frames = profile != null && profile.unicodeEnabled()
                ? UNICODE_FRAMES
                : ASCII_FRAMES;
        if (elapsed == null || elapsed.isNegative() || elapsed.isZero()) {
            return frames[0];
        }

        // 先对秒数取模再计算，避免极长 Duration 转毫秒时溢出。
        long wholeSecondFrames = Math.floorMod(elapsed.getSeconds(), frames.length) * FRAMES_PER_SECOND;
        long fractionFrames = elapsed.getNano() / FRAME_PERIOD.toNanos();
        int index = (int) ((wholeSecondFrames + fractionFrames) % frames.length);
        return frames[index];
    }
}
