package com.lunacode.agent;

public record GitStatusSnapshot(boolean insideWorkTree, String branch, Boolean dirty, String summary) {
    public static GitStatusSnapshot unknown(String summary) {
        return new GitStatusSnapshot(false, "unknown", null, summary == null || summary.isBlank() ? "unknown" : summary);
    }

    public String render() {
        String dirtyText = dirty == null ? "unknown" : dirty ? "dirty" : "clean";
        return "insideWorkTree=" + insideWorkTree + ", branch=" + branch + ", dirty=" + dirtyText + ", summary=" + summary;
    }
}
