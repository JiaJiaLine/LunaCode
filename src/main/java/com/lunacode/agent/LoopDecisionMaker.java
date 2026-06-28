package com.lunacode.agent;

public final class LoopDecisionMaker {
    public LoopDecision decide(LoopContext context, AgentTurnResult turnResult) {
        if (context.cancellationToken() != null && context.cancellationToken().isCancellationRequested()) {
            return new LoopDecision.StopCancelled();
        }
        if (turnResult.finalState() == AgentTurnState.FAILED) {
            return new LoopDecision.StopError(turnResult.errorSummary());
        }
        if (context.currentIteration() >= context.config().maxIterations()) {
            return new LoopDecision.StopWithLimit(context.config().maxIterations());
        }
        if (context.consecutiveUnknownToolCount() >= context.config().maxConsecutiveUnknownTools()) {
            return new LoopDecision.StopUnknownTools(context.consecutiveUnknownToolCount());
        }
        if (turnResult.toolUses() == null || turnResult.toolUses().isEmpty()) {
            return new LoopDecision.Complete();
        }
        return new LoopDecision.ContinueWithTools(turnResult.toolUses());
    }
}
