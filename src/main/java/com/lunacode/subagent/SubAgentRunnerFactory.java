package com.lunacode.subagent;

public interface SubAgentRunnerFactory {
    SubAgentRunHandle start(SubAgentLaunchRequest request);
}
