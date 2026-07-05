package com.lunacode.hook;

public enum HookSourceLevel {
    PROJECT(0),
    USER(1),
    LOCAL(2);

    private final int order;

    HookSourceLevel(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }
}
