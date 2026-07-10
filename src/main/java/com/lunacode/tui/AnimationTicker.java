package com.lunacode.tui;

public interface AnimationTicker extends AutoCloseable {
    void start(Runnable tick);

    void stop();

    boolean running();

    @Override
    void close();
}
