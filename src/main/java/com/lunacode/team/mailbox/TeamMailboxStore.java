package com.lunacode.team.mailbox;

import java.util.List;

public interface TeamMailboxStore {
    TeamMessageRecord append(String mailboxId, TeamMessageRecord message);

    List<TeamMessageRecord> read(String mailboxId);
}
