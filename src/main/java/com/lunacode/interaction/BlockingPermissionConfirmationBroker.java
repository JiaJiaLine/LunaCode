package com.lunacode.interaction;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class BlockingPermissionConfirmationBroker implements PermissionConfirmationBroker {
    private final AtomicReference<PendingConfirmation> pending = new AtomicReference<>();

    @Override
    public PermissionConfirmationAnswer confirm(PermissionConfirmationRequest request) {
        Objects.requireNonNull(request, "request");
        PendingConfirmation next = new PendingConfirmation(request, new CompletableFuture<>());
        if (!pending.compareAndSet(null, next)) {
            throw new IllegalStateException("已有待确认的权限请求");
        }
        try {
            return next.answer().join();
        } finally {
            pending.compareAndSet(next, null);
        }
    }

    public boolean hasPendingConfirmation() {
        return pending.get() != null;
    }

    public String pendingPrompt() {
        PendingConfirmation current = pending.get();
        return current == null ? null : current.request().prompt();
    }

    public boolean answer(String answer) {
        PendingConfirmation current = pending.get();
        return current != null && current.answer().complete(parseAnswer(answer));
    }

    public void cancelPending() {
        PendingConfirmation current = pending.getAndSet(null);
        if (current != null) {
            current.answer().complete(PermissionConfirmationAnswer.DENY);
        }
    }

    private PermissionConfirmationAnswer parseAnswer(String answer) {
        String normalized = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("always")
                || normalized.equals("allow always")
                || normalized.equals("始终允许")
                || normalized.equals("永久允许")) {
            return PermissionConfirmationAnswer.ALLOW_ALWAYS;
        }
        if (normalized.equals("y")
                || normalized.equals("yes")
                || normalized.equals("ok")
                || normalized.equals("approve")
                || normalized.equals("allow")
                || normalized.equals("确认")
                || normalized.equals("允许")
                || normalized.equals("同意")
                || normalized.equals("本次允许")) {
            return PermissionConfirmationAnswer.ALLOW_ONCE;
        }
        return PermissionConfirmationAnswer.DENY;
    }

    private record PendingConfirmation(PermissionConfirmationRequest request, CompletableFuture<PermissionConfirmationAnswer> answer) {}
}
