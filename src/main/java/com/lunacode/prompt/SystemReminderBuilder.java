package com.lunacode.prompt;

import com.lunacode.tool.DeferredToolSummary;

import java.util.ArrayList;
import java.util.List;

public final class SystemReminderBuilder {
    private final PlanModeReminderPolicy planModeReminderPolicy;
    private final DeferredToolReminderPolicy deferredToolReminderPolicy;

    public SystemReminderBuilder() {
        this(new PlanModeReminderPolicy(), new DeferredToolReminderPolicy());
    }

    public SystemReminderBuilder(PlanModeReminderPolicy planModeReminderPolicy) {
        this(planModeReminderPolicy, new DeferredToolReminderPolicy());
    }

    public SystemReminderBuilder(PlanModeReminderPolicy planModeReminderPolicy, DeferredToolReminderPolicy deferredToolReminderPolicy) {
        this.planModeReminderPolicy = planModeReminderPolicy;
        this.deferredToolReminderPolicy = deferredToolReminderPolicy;
    }

    public List<SystemReminder> build(ModeInjectionState state) {
        return build(state, List.of());
    }

    public List<SystemReminder> build(ModeInjectionState state, List<DeferredToolSummary> deferredTools) {
        List<SystemReminder> reminders = new ArrayList<>();
        planModeReminderPolicy.createReminder(state).ifPresent(reminders::add);
        SystemReminder mcpReminder = deferredToolReminderPolicy.createReminder(state, deferredTools);
        if (mcpReminder != null) {
            reminders.add(mcpReminder);
        }
        return List.copyOf(reminders);
    }
}
