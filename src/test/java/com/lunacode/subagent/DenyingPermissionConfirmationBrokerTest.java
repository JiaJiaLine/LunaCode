package com.lunacode.subagent;

import com.lunacode.interaction.PermissionConfirmationAnswer;
import com.lunacode.interaction.PermissionConfirmationRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenyingPermissionConfirmationBrokerTest {
    @Test
    void deniesWithoutBlockingForUserInput() {
        DenyingPermissionConfirmationBroker broker = new DenyingPermissionConfirmationBroker();

        PermissionConfirmationAnswer answer = broker.confirm(new PermissionConfirmationRequest("req-1", "Bash", "allow?"));

        assertEquals(PermissionConfirmationAnswer.DENY, answer);
        assertTrue(broker.lastDenyReason().contains("Bash"));
    }
}