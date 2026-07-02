package com.lunacode.tui;

import com.lunacode.orchestrator.StatusSnapshot;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LanternaLunaTuiStatusContextTest {
    @Test
    void keepsIdleSessionAndMemoryContextOutOfPrintedStatusLines() throws Exception {
        StatusSnapshot status = StatusSnapshot.idle("test", "model")
                .withSessionAndMemory("20250115-143000-a3f7", true, "updated");

        assertFalse(shouldPrintStatus(status));
        assertEquals("", printStatus(status));
        assertEquals("[DEFAULT s:20250115-143000-a3f7 mem:on:updated] ", promptContext(status));
    }


    @Test
    void promptContextShowsPlanModeWithoutSessionOrMemory() throws Exception {
        StatusSnapshot status = StatusSnapshot.idle("test", "model")
                .withAgentMode(com.lunacode.runtime.AgentMode.PLAN);

        assertEquals("[PLAN] ", promptContext(status));
    }    private boolean shouldPrintStatus(StatusSnapshot status) throws Exception {
        LanternaLunaTui tui = new LanternaLunaTui(null, null);
        Method method = LanternaLunaTui.class.getDeclaredMethod("shouldPrintStatus", StatusSnapshot.class);
        method.setAccessible(true);
        return (boolean) method.invoke(tui, status);
    }

    private String promptContext(StatusSnapshot status) throws Exception {
        LanternaLunaTui tui = new LanternaLunaTui(null, null);
        Method method = LanternaLunaTui.class.getDeclaredMethod("promptContext", StatusSnapshot.class);
        method.setAccessible(true);
        return (String) method.invoke(tui, status);
    }

    private String printStatus(StatusSnapshot status) throws Exception {
        LanternaLunaTui tui = new LanternaLunaTui(null, null);
        Method method = LanternaLunaTui.class.getDeclaredMethod("printStatus", PrintWriter.class, StatusSnapshot.class);
        method.setAccessible(true);
        StringWriter output = new StringWriter();
        method.invoke(tui, new PrintWriter(output, true), status);
        return output.toString();
    }
}