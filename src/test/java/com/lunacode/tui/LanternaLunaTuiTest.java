package com.lunacode.tui;

import com.lunacode.orchestrator.StatusSnapshot;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanternaLunaTuiTest {
    @Test
    void printsPermissionStatusWithPermissionLabel() throws Exception {
        String output = printStatus(new StatusSnapshot("test", "model", null, null, "waiting_permission", "确认写入 src/Main.java", "WriteFile", "确认写入 src/Main.java"));

        assertEquals("Luna [permission default] 确认写入 src/Main.java" + System.lineSeparator(), output);
    }

    @Test
    void suppressesDuplicatePermissionStatus() throws Exception {
        StatusSnapshot status = new StatusSnapshot("test", "model", null, null, "waiting_permission", "确认写入 src/Main.java", "WriteFile", "确认写入 src/Main.java");
        LanternaLunaTui tui = new LanternaLunaTui(null, null);
        Method method = LanternaLunaTui.class.getDeclaredMethod("printStatus", PrintWriter.class, StatusSnapshot.class);
        method.setAccessible(true);
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);

        method.invoke(tui, writer, status);
        method.invoke(tui, writer, status);

        assertEquals("Luna [permission default] 确认写入 src/Main.java" + System.lineSeparator(), output.toString());
    }
    @Test
    void printsToolRunningStatusWithReadableSummary() throws Exception {
        String output = printStatus(new StatusSnapshot("test", "model", null, null, "tool_running", null, "Bash", "Luna正在使用\"Bash\"工具执行\"mvn test\""));

        assertEquals("Luna [tool] Luna正在使用\"Bash\"工具执行\"mvn test\"" + System.lineSeparator(), output);
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