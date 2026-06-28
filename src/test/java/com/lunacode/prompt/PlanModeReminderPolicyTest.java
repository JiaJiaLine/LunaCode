package com.lunacode.prompt;

import com.lunacode.runtime.AgentMode;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlanModeReminderPolicyTest {
    private final PlanModeReminderPolicy policy = new PlanModeReminderPolicy();
    private final Path planFile = Path.of(".lunacode/plan.md");

    @Test
    void createsFullRepeatAndBriefPlanModeReminders() {
        String first = policy.createReminder(new ModeInjectionState(AgentMode.PLAN, 1, planFile, 3)).orElseThrow().content();
        String brief = policy.createReminder(new ModeInjectionState(AgentMode.PLAN, 2, planFile, 3)).orElseThrow().content();
        String repeat = policy.createReminder(new ModeInjectionState(AgentMode.PLAN, 3, planFile, 3)).orElseThrow().content();

        assertTrue(first.contains("先澄清需求"));
        assertTrue(first.contains("再探索代码"));
        assertTrue(first.contains("再生成计划"));
        assertTrue(first.contains("避免执行实际修改"));
        assertTrue(brief.contains("Plan Mode 仍然有效"));
        assertTrue(repeat.contains("继续保持先澄清"));
    }

    @Test
    void defaultModeDoesNotCreatePlanModeReminder() {
        assertTrue(policy.createReminder(new ModeInjectionState(AgentMode.DEFAULT, 1, planFile, 3)).isEmpty());
    }

    @Test
    void rendererMarksReminderAsSystemContextNotUserRequest() {
        SystemReminder reminder = policy.createReminder(new ModeInjectionState(AgentMode.PLAN, 1, planFile, 3)).orElseThrow();
        String rendered = new SystemReminderRenderer().render(reminder);

        assertTrue(rendered.contains("系统级补充上下文"));
        assertTrue(rendered.contains("不是用户请求"));
    }
}
