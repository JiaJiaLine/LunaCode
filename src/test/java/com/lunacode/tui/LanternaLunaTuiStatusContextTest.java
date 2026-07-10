package com.lunacode.tui;

import com.lunacode.orchestrator.StatusSnapshot;
import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanternaLunaTuiStatusContextTest {
    @Test
    void compactContextOnlyKeepsModelModePermissionAndSession() throws Exception {
        StatusSnapshot status = new StatusSnapshot(
                "provider-must-not-leak",
                "claude-sonnet",
                123,
                456,
                "idle",
                null
        ).withSessionAndMemory("20250115-a3f7", true, "updated");

        String context = promptContext(status);

        assertEquals(
                "claude-sonnet | mode:Agent | perm:default | s:20250115-a3f7",
                context
        );
        assertFalse(context.contains("provider-must-not-leak"));
        assertFalse(context.contains("123"));
        assertFalse(context.contains("456"));
        assertFalse(context.toLowerCase().contains("memory"));
        assertFalse(context.contains("updated"));
    }

    @Test
    void compactContextReflectsPlanAndPermissionModesAndOmitsMissingFields() throws Exception {
        StatusSnapshot plan = StatusSnapshot.idle("test", "model-x")
                .withAgentMode(AgentMode.PLAN)
                .withPermissionMode(PermissionMode.PLAN);
        StatusSnapshot missingModel = StatusSnapshot.idle("test", "")
                .withSessionAndMemory(null, false, null);

        assertEquals("model-x | mode:Plan | perm:plan", promptContext(plan));
        assertEquals("mode:Agent | perm:default", promptContext(missingModel));
    }

    @Test
    void idleAndRunningStatesDoNotBecomePermanentStatusLines() throws Exception {
        StatusSnapshot idle = StatusSnapshot.idle("test", "model");
        StatusSnapshot toolRunning = new StatusSnapshot(
                "test", "model", null, null,
                "tool_running", null, "Bash", "mvn test"
        );

        assertFalse(shouldPrintStatus(idle));
        assertFalse(shouldPrintStatus(toolRunning));
        assertEquals("", printStatus(idle));
        assertEquals("", printStatus(toolRunning));
    }

    @Test
    void detailedStatusInfoKeepsEveryDiagnosticLine() throws Exception {
        StatusSnapshot detailed = new StatusSnapshot(
                "anthropic", "claude-sonnet", 123, 456,
                "idle",
                "Provider: anthropic\nModel: claude-sonnet\n输入 token: 123\n输出 token: 456\n会话: s-1\n记忆: on:updated"
        );

        String output = printStatus(detailed);

        assertTrue(output.contains("Provider: anthropic"));
        assertTrue(output.contains("Model: claude-sonnet"));
        assertTrue(output.contains("输入 token: 123"));
        assertTrue(output.contains("输出 token: 456"));
        assertTrue(output.contains("会话: s-1"));
        assertTrue(output.contains("记忆: on:updated"));
    }

    @Test
    void finalStatesUseDistinctPlainTextSymbolsAndLabels() throws Exception {
        assertTrue(printStatus(new StatusSnapshot(
                "p", "m", null, null, "waiting_user", "请提供目录"
        )).startsWith("? 等待回答"));
        assertTrue(printStatus(new StatusSnapshot(
                "p", "m", null, null, "cancelled", "用户取消"
        )).startsWith("! 已取消"));
        assertTrue(printStatus(new StatusSnapshot(
                "p", "m", null, null, "warning", "上下文接近上限"
        )).startsWith("! 警告"));
        assertTrue(printStatus(new StatusSnapshot(
                "p", "m", null, null, "error", "Provider 不可用"
        )).startsWith("x 错误"));
        assertTrue(printStatus(new StatusSnapshot(
                "p", "m", null, null, "idle", "状态详情"
        )).startsWith("(L) 信息"));
    }

    private boolean shouldPrintStatus(StatusSnapshot status) throws Exception {
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
        Method method = LanternaLunaTui.class.getDeclaredMethod(
                "printStatus", PrintWriter.class, StatusSnapshot.class
        );
        method.setAccessible(true);
        StringWriter output = new StringWriter();
        method.invoke(tui, new PrintWriter(output, true), status);
        return output.toString();
    }
}
