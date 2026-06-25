package com.lunacode.stream;

public record SseEvent(String event, String data) {}