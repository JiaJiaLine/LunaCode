package com.lunacode.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.tool.ToolUse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoopDecisionMakerTest {
    private final LoopDecisionMaker maker = new LoopDecisionMaker();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void completesWhenNoToolUse() {
        assertInstanceOf(LoopDecision.Complete.class, maker.decide(context(1, 0, false), result(List.of(), AgentTurnState.COMPLETED)));
    }

    @Test
    void continuesWhenToolsExist() {
        ToolUse use = new ToolUse("1", "ReadFile", mapper.createObjectNode());
        assertInstanceOf(LoopDecision.ContinueWithTools.class, maker.decide(context(1, 0, false), result(List.of(use), AgentTurnState.COMPLETED)));
    }

    @Test
    void stopsForLimitCancelUnknownAndError() {
        assertInstanceOf(LoopDecision.StopWithLimit.class, maker.decide(context(8, 0, false), result(List.of(new ToolUse("1", "ReadFile", mapper.createObjectNode())), AgentTurnState.COMPLETED)));
        assertInstanceOf(LoopDecision.StopUnknownTools.class, maker.decide(context(1, 3, false), result(List.of(new ToolUse("1", "Missing", mapper.createObjectNode())), AgentTurnState.COMPLETED)));
        assertInstanceOf(LoopDecision.StopError.class, maker.decide(context(1, 0, false), result(List.of(), AgentTurnState.FAILED)));
        assertInstanceOf(LoopDecision.StopCancelled.class, maker.decide(context(1, 0, true), result(List.of(), AgentTurnState.COMPLETED)));
    }

    private LoopContext context(int iteration, int unknown, boolean cancelled) {
        CancellationToken token = new CancellationToken();
        if (cancelled) {
            token.cancel();
        }
        return new LoopContext(new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of("plan.md"), 8, 3, Clock.systemUTC()), token, iteration, unknown, TokenUsage.unknown());
    }

    private AgentTurnResult result(List<ToolUse> uses, AgentTurnState state) {
        return new AgentTurnResult(1, "a1", "text", uses, TokenUsage.unknown(), state, state == AgentTurnState.FAILED ? "boom" : null);
    }
}