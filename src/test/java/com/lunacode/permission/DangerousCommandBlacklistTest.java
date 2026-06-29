package com.lunacode.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DangerousCommandBlacklistTest {
    private final DangerousCommandBlacklist blacklist = new DangerousCommandBlacklist();

    @Test
    void blocksKnownDangerousCommands() {
        assertTrue(blacklist.firstMatch("rm -rf /").isPresent());
        assertTrue(blacklist.firstMatch(":(){ :|:& };:").isPresent());
        assertTrue(blacklist.firstMatch("mkfs.ext4 /dev/sda1").isPresent());
    }

    @Test
    void allowsOrdinaryCommands() {
        assertFalse(blacklist.firstMatch("git status --short").isPresent());
    }
}
