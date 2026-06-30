package com.lunacode.interaction;

import com.lunacode.permission.PermissionMode;

public record PermissionConfirmationRequest(
        String requestId,
        String toolName,
        String prompt,
        PermissionMode permissionMode,
        String targetSummary,
        String reason,
        String suggestedAllowRule
) {
    public PermissionConfirmationRequest(String requestId, String toolName, String prompt) {
        this(requestId, toolName, prompt, PermissionMode.DEFAULT, "", "", null);
    }
}
