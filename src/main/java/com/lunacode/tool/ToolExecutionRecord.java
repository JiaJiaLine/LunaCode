package com.lunacode.tool;

import java.time.Duration;

public record ToolExecutionRecord(
        ToolUse toolUse,
        ToolResult result,
        Duration duration
) {}
