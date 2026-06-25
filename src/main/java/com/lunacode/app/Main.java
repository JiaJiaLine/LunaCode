package com.lunacode.app;

public class Main {
    public static void main(String[] args) {
        System.setProperty("org.jline.terminal.disableDeprecatedProviderWarning", "true");
        new LunaCodeApplication().run(args);
    }
}