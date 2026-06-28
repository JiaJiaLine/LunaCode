package com.lunacode.agent;

public record UserQuestionRequest(
        String requestId,
        String question
) {}
