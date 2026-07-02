package com.lunacode.session;

import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ConversationMessageSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultSessionService implements SessionService {
    private final SessionStore store;
    private final SessionRecoveryPolicy recoveryPolicy;
    private final ContextConfig contextConfig;
    private final Duration ttl;
    private final AtomicReference<SessionId> current = new AtomicReference<>();

    public DefaultSessionService(SessionStore store, ContextConfig contextConfig) {
        this(store, new SessionRecoveryPolicy(), contextConfig, JsonlSessionStore.DEFAULT_TTL);
    }

    DefaultSessionService(SessionStore store, SessionRecoveryPolicy recoveryPolicy, ContextConfig contextConfig, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store");
        this.recoveryPolicy = Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
        this.contextConfig = contextConfig == null ? ContextConfig.defaults() : contextConfig;
        this.ttl = ttl == null ? JsonlSessionStore.DEFAULT_TTL : ttl;
    }

    @Override
    public SessionRecoveryResult restoreLatestOrCreate() {
        store.deleteExpired(ttl);
        List<SessionInfo> candidates = store.listSessions().stream()
                .filter(info -> !info.expired())
                .sorted(Comparator.comparing(SessionInfo::lastActiveAt).reversed())
                .toList();
        if (candidates.isEmpty()) {
            SessionId id = newSession();
            return new SessionRecoveryResult(id.value(), List.of(), List.of("未找到可恢复会话，已创建新会话。"), java.util.Optional.empty(), false, false);
        }
        return resume(new SessionId(candidates.get(0).id()));
    }

    @Override
    public SessionRecoveryResult resume(SessionId id) {
        Objects.requireNonNull(id, "id");
        SessionLoadResult loaded = store.load(id);
        current.set(id);
        return recoveryPolicy.recover(loaded, contextConfig);
    }

    @Override
    public SessionId newSession() {
        SessionId id = store.createSessionId();
        current.set(id);
        try {
            Files.createDirectories(store.pathFor(id).getParent());
            if (!Files.exists(store.pathFor(id))) {
                Files.createFile(store.pathFor(id));
            }
        } catch (IOException e) {
            throw new JsonlSessionStore.SessionStoreException("创建新会话文件失败: " + id.value(), e);
        }
        return id;
    }

    @Override
    public SessionInfo currentSession() {
        SessionId id = current.get();
        if (id == null) {
            id = newSession();
        }
        SessionId currentId = id;
        return store.listSessions().stream()
                .filter(info -> info.id().equals(currentId.value()))
                .findFirst()
                .orElseGet(() -> new SessionInfo(currentId.value(), store.pathFor(currentId), "新会话", 0, Instant.now(), Instant.now(), false));
    }

    @Override
    public List<SessionInfo> listSessions() {
        return store.listSessions();
    }

    @Override
    public void appendCurrent(ConversationMessageSnapshot message) {
        SessionId id = current.get();
        if (id == null) {
            id = newSession();
        }
        store.append(id, message);
    }
}
