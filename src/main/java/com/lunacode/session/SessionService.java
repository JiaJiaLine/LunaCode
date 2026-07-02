package com.lunacode.session;

import com.lunacode.conversation.ConversationMessageSnapshot;

import java.util.List;

public interface SessionService {
    SessionRecoveryResult restoreLatestOrCreate();

    SessionRecoveryResult resume(SessionId id);

    SessionId newSession();

    SessionInfo currentSession();

    List<SessionInfo> listSessions();

    void appendCurrent(ConversationMessageSnapshot message);
}
