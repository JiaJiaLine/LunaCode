package com.lunacode.interaction;

public record PermissionConfirmationRequest(
        String requestId,
        String toolName,
        String prompt
) {}
