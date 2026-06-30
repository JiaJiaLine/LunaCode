package com.lunacode.mcp;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpToolNameAllocatorTest {
    @Test
    void legalizesAndAvoidsReservedNameConflicts() {
        McpToolNameAllocator allocator = new McpToolNameAllocator(Set.of("mcp_git_hub_search_issues"));

        String allocated = allocator.allocate("git hub", "search/issues");

        assertTrue(allocated.startsWith("mcp_git_hub_search_issues"));
        assertNotEquals("mcp_git_hub_search_issues", allocated);
        assertTrue(allocated.matches("[A-Za-z][A-Za-z0-9_-]*"));
    }

    @Test
    void collisionSuffixIsStable() {
        Set<String> reserved = Set.of("mcp_server_tool");

        String first = new McpToolNameAllocator(reserved).allocate("server", "tool");
        String second = new McpToolNameAllocator(reserved).allocate("server", "tool");

        assertEquals(first, second);
    }
}
