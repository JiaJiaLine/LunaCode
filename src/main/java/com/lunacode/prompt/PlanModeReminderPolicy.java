package com.lunacode.prompt;

import com.lunacode.runtime.AgentMode;

import java.util.Optional;

public final class PlanModeReminderPolicy {
    public Optional<SystemReminder> createReminder(ModeInjectionState state) {
        if (state.mode() != AgentMode.PLAN) {
            return Optional.empty();
        }
        if (state.turnIndex() == 1) {
            return Optional.of(new SystemReminder(SystemReminderKind.PLAN_MODE, fullInstruction(state), state.turnIndex()));
        }
        if (state.turnIndex() % state.repeatInterval() == 0) {
            return Optional.of(new SystemReminder(SystemReminderKind.PLAN_MODE, repeatInstruction(state), state.turnIndex()));
        }
        return Optional.of(new SystemReminder(SystemReminderKind.PLAN_MODE, briefInstruction(state), state.turnIndex()));
    }

    private String fullInstruction(ModeInjectionState state) {
        return """
                当前处于 Plan Mode。先澄清需求，再探索代码，再生成计划；避免执行实际修改。
                如需写计划，只写入指定 plan 文件：%s。
                权限系统仍与 Default 模式一致：写非 plan 文件、执行命令或其他需要确认的操作会触发正常确认，不要把 ASK 当成硬性 DENY。
                """.formatted(state.planFile()).strip();
    }

    private String repeatInstruction(ModeInjectionState state) {
        return """
                Plan Mode 仍然有效。继续保持先澄清、再探索、再写计划；不要把规划禁令写入静态 System Prompt。
                指定 plan 文件：%s。
                """.formatted(state.planFile()).strip();
    }

    private String briefInstruction(ModeInjectionState state) {
        return "Plan Mode 仍然有效；保持规划语境，避免实际修改。指定 plan 文件：" + state.planFile();
    }
}
