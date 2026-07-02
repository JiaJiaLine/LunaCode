package com.lunacode.session;

import com.lunacode.conversation.ConversationMessageSnapshot;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface SessionStore {
    SessionId createSessionId();

    Path pathFor(SessionId id);

    void append(SessionId id, ConversationMessageSnapshot message);

    SessionLoadResult load(SessionId id);

    List<SessionInfo> listSessions();

    List<SessionInfo> deleteExpired(Duration ttl);
}
