package com.lunacode.tui;

import java.time.Duration;
import java.time.Instant;

public record TuiActivity(
        String id,
        ActivityKind kind,
        ActivityPhase phase,
        String title,
        String detail,
        Instant startedAt,
        Duration finalDuration,
        String errorSummary
) {
    public TuiActivity {
        id = id == null ? "" : id;
        kind = kind == null ? ActivityKind.MODEL : kind;
        phase = phase == null ? ActivityPhase.RUNNING : phase;
        title = title == null ? "" : title;
        detail = detail == null ? "" : detail;
        startedAt = startedAt == null ? Instant.EPOCH : startedAt;
        errorSummary = errorSummary == null ? "" : errorSummary;
    }

    public Duration elapsedAt(Instant now) {
        if (finalDuration != null) {
            return nonNegative(finalDuration);
        }
        Instant safeNow = now == null ? startedAt : now;
        if (safeNow.isBefore(startedAt)) {
            return Duration.ZERO;
        }
        return Duration.between(startedAt, safeNow);
    }

    private Duration nonNegative(Duration value) {
        return value.isNegative() ? Duration.ZERO : value;
    }
}
