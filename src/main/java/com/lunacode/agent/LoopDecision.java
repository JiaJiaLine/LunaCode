package com.lunacode.agent;

import com.lunacode.tool.ToolUse;

import java.util.List;

public sealed interface LoopDecision permits
        LoopDecision.ContinueWithTools,
        LoopDecision.Complete,
        LoopDecision.StopWithLimit,
        LoopDecision.StopCancelled,
        LoopDecision.StopUnknownTools,
        LoopDecision.StopError {

    record ContinueWithTools(List<ToolUse> toolUses) implements LoopDecision {}

    record Complete() implements LoopDecision {}

    record StopWithLimit(int maxIterations) implements LoopDecision {}

    record StopCancelled() implements LoopDecision {}

    record StopUnknownTools(int count) implements LoopDecision {}

    record StopError(String summary) implements LoopDecision {}
}
