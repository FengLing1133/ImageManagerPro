package com.yang;

/**
 * 独立的启动类，由于没有继承 javafx.application.Application，
 * JVM 在启动时不会强制检查 JavaFX 模块路径，从而可以直接在 IDE 中运行。
 */
public class App {
    public static void main(String[] args) {
        ImageManagerProApplication.main(args);
    }
}

