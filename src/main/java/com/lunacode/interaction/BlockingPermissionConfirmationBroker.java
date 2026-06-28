package com.lunacode.interaction;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class BlockingPermissionConfirmationBroker implements PermissionConfirmationBroker {
    private final AtomicReference<PendingConfirmation> pending = new AtomicReference<>();

    @Override
    public boolean confirm(PermissionConfirmationRequest request) {
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
        return current != null && current.answer().complete(isApproval(answer));
    }

    public void cancelPending() {
        PendingConfirmation current = pending.getAndSet(null);
        if (current != null) {
            current.answer().complete(false);
        }
    }

    private boolean isApproval(String answer) {
        String normalized = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("y")
                || normalized.equals("yes")
                || normalized.equals("ok")
                || normalized.equals("approve")
                || normalized.equals("allow")
                || normalized.equals("确认")
                || normalized.equals("允许")
                || normalized.equals("同意");
    }

    private record PendingConfirmation(PermissionConfirmationRequest request, CompletableFuture<Boolean> answer) {}
}
