package com.lunacode.session;

import com.fasterxml.jackson.databind.JsonNode;

public record SessionRecord(
        String role,
        JsonNode content,
        long ts
) {}
