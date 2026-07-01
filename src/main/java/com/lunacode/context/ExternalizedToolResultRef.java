package com.lunacode.context;

import java.nio.file.Path;

public record ExternalizedToolResultRef(
        String messageId,
        String toolUseId,
        String toolName,
        Path path,
        int originalChars,
        int previewChars,
        boolean error
) {}
