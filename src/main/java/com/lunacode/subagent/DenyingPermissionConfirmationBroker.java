package com.lunacode.subagent;

import com.lunacode.interaction.PermissionConfirmationAnswer;
import com.lunacode.interaction.PermissionConfirmationBroker;
import com.lunacode.interaction.PermissionConfirmationRequest;

import java.util.concurrent.atomic.AtomicReference;

public final class DenyingPermissionConfirmationBroker implements PermissionConfirmationBroker {
    private final AtomicReference<String> lastDenyReason = new AtomicReference<>("");

    @Override
    public PermissionConfirmationAnswer confirm(PermissionConfirmationRequest request) {
        String toolName = request == null ? "未知工具" : request.toolName();
        lastDenyReason.set("子 Agent 非交互运行，已拒绝需要用户确认的工具调用: " + toolName);
        return PermissionConfirmationAnswer.DENY;
    }

    public String lastDenyReason() {
        return lastDenyReason.get();
    }
}
