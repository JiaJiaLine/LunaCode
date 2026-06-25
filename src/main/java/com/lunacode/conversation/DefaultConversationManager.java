package com.lunacode.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DefaultConversationManager implements ConversationManager {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<MutableMessage> messages = new ArrayList<>();
    private final Clock clock;

    public DefaultConversationManager() {
        this(Clock.systemUTC());
    }

    DefaultConversationManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String addMessage(MessageRole role, String content) {
        Objects.requireNonNull(role, "role");
        lock.writeLock().lock();
        try {
            String id = newId();
            MessageStatus status = role == MessageRole.ASSISTANT ? MessageStatus.STREAMING : MessageStatus.COMPLETE;
            messages.add(new MutableMessage(id, role, status, Instant.now(clock), TokenUsage.unknown(), safe(content), null));
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String addStreamingAssistantMessage() {
        lock.writeLock().lock();
        try {
            String id = newId();
            messages.add(new MutableMessage(id, MessageRole.ASSISTANT, MessageStatus.STREAMING, Instant.now(clock), TokenUsage.unknown(), "", null));
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void appendContent(String messageId, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            if (message.role != MessageRole.ASSISTANT || message.status != MessageStatus.STREAMING) {
                throw new IllegalStateException("只能追加正在流式生成的 assistant 消息");
            }
            message.content += delta;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void completeMessage(String messageId, TokenUsage usage) {
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            message.status = MessageStatus.COMPLETE;
            message.usage = message.usage.merge(usage);
            message.errorSummary = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void failMessage(String messageId, String errorSummary) {
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            message.status = MessageStatus.ERROR;
            message.errorSummary = safe(errorSummary);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<InternalMessage> snapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(messages.stream().map(MutableMessage::snapshot).toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<ApiMessage> toAPIFormat() {
        lock.readLock().lock();
        try {
            List<ApiMessage> result = new ArrayList<>();
            for (MutableMessage message : messages) {
                if (message.status == MessageStatus.ERROR) {
                    continue;
                }
                if (message.role != MessageRole.USER && message.role != MessageRole.ASSISTANT) {
                    continue;
                }
                if (message.content.isBlank()) {
                    continue;
                }
                String role = apiRole(message.role);
                if (result.isEmpty()) {
                    if (!role.equals("user")) {
                        continue;
                    }
                    result.add(new ApiMessage(role, message.content));
                    continue;
                }
                ApiMessage last = result.get(result.size() - 1);
                if (last.role().equals(role)) {
                    result.set(result.size() - 1, new ApiMessage(role, last.content() + "\n\n" + message.content));
                } else {
                    result.add(new ApiMessage(role, message.content));
                }
            }
            return List.copyOf(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    private MutableMessage find(String messageId) {
        return messages.stream()
                .filter(message -> message.id.equals(messageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("消息不存在: " + messageId));
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String apiRole(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM, TOOL -> throw new IllegalArgumentException("不支持的 API 角色: " + role);
        };
    }

    private static class MutableMessage {
        private final String id;
        private final MessageRole role;
        private MessageStatus status;
        private final Instant timestamp;
        private TokenUsage usage;
        private String content;
        private String errorSummary;

        private MutableMessage(String id, MessageRole role, MessageStatus status, Instant timestamp, TokenUsage usage, String content, String errorSummary) {
            this.id = id;
            this.role = role;
            this.status = status;
            this.timestamp = timestamp;
            this.usage = usage;
            this.content = content;
            this.errorSummary = errorSummary;
        }

        private InternalMessage snapshot() {
            return new InternalMessage(id, role, status, timestamp, usage, content, errorSummary);
        }
    }
}