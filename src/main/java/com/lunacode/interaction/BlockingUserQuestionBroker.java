package com.lunacode.interaction;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class BlockingUserQuestionBroker implements UserQuestionBroker {
    private final AtomicReference<PendingQuestion> pending = new AtomicReference<>();

    @Override
    public String ask(UserQuestionRequest request) {
        Objects.requireNonNull(request, "request");
        PendingQuestion next = new PendingQuestion(request, new CompletableFuture<>());
        if (!pending.compareAndSet(null, next)) {
            throw new IllegalStateException("已有待回答的澄清问题");
        }
        try {
            return next.answer().join();
        } finally {
            pending.compareAndSet(next, null);
        }
    }

    public boolean hasPendingQuestion() {
        return pending.get() != null;
    }

    public String pendingQuestionText() {
        PendingQuestion current = pending.get();
        return current == null ? null : current.request().question();
    }

    public boolean answer(String answer) {
        PendingQuestion current = pending.get();
        return current != null && current.answer().complete(answer == null ? "" : answer);
    }

    public void cancelPending() {
        PendingQuestion current = pending.getAndSet(null);
        if (current != null) {
            current.answer().completeExceptionally(new IllegalStateException("用户已取消"));
        }
    }

    private record PendingQuestion(UserQuestionRequest request, CompletableFuture<String> answer) {}
}
