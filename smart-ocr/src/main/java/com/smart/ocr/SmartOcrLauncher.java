package com.smart.ocr;

/**
 * SmartOCR启动引导类
 * 解决JavaFX与Spring Boot集成时的类加载问题
 */
public class SmartOcrLauncher {

    public static void main(String[] args) {
        SmartOcrGuiApplication.main(args);
    }
}
