package com.lunacode.interaction;

public record UserQuestionRequest(
        String requestId,
        String question
) {}
