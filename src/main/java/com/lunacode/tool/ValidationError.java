package com.lunacode.tool;

public record ValidationError(
        String code,
        String message
) {}
