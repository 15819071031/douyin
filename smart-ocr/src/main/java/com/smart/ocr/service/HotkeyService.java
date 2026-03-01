package com.smart.ocr.service;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.smart.ocr.config.AppConfig;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局热键服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotkeyService implements NativeKeyListener {

    private final AppConfig appConfig;
    private Runnable screenshotCallback;
    private boolean registered = false;

    /**
     * 注册全局热键
     */
    public void registerHotkeys() {
        if (registered) return;

        try {
            // 禁用JNativeHook的日志输出
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            registered = true;
            log.info("全局热键注册成功，截图热键: {}", appConfig.getScreenshotHotkey());
        } catch (NativeHookException e) {
            log.error("全局热键注册失败", e);
        }
    }

    /**
     * 注销全局热键
     */
    public void unregisterHotkeys() {
        if (!registered) return;

        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
            registered = false;
            log.info("全局热键已注销");
        } catch (NativeHookException e) {
            log.error("全局热键注销失败", e);
        }
    }

    /**
     * 设置截图回调
     */
    public void setScreenshotCallback(Runnable callback) {
        this.screenshotCallback = callback;
    }

    /**
     * 触发截图
     */
    public void triggerScreenshot() {
        if (screenshotCallback != null) {
            Platform.runLater(screenshotCallback);
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        String keyText = NativeKeyEvent.getKeyText(e.getKeyCode());
        
        // 检查是否按下截图热键
        if (keyText.equalsIgnoreCase(appConfig.getScreenshotHotkey())) {
            log.debug("检测到截图热键: {}", keyText);
            triggerScreenshot();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        // 不处理
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // 不处理
    }
}
