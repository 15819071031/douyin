package com.smart.ocr.util;

import com.smart.ocr.config.AdbConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * ADB命令工具类
 * 用于控制Android设备
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdbUtil {

    private final AdbConfig adbConfig;

    /**
     * 执行ADB命令
     */
    public String executeCommand(String command) {
        try {
            String fullCommand = String.format("%s -s %s %s",
                    adbConfig.getPath(), adbConfig.getDeviceId(), command);
            log.debug("执行ADB命令: {}", fullCommand);

            Process process = Runtime.getRuntime().exec(fullCommand);
            process.waitFor(10, TimeUnit.SECONDS);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString().trim();
        } catch (Exception e) {
            log.error("ADB命令执行失败: {}", command, e);
            return "";
        }
    }

    /**
     * 点击屏幕
     */
    public void tap(int x, int y) {
        executeCommand(String.format("shell input tap %d %d", x, y));
        log.debug("点击坐标: ({}, {})", x, y);
    }

    /**
     * 上滑（刷下一个视频）
     */
    public void swipeUp() {
        int centerX = adbConfig.getScreenWidth() / 2;
        executeCommand(String.format("shell input swipe %d 1500 %d 500 300", centerX, centerX));
        log.debug("上滑刷下一个视频");
    }

    /**
     * 下滑（回到上一个视频）
     */
    public void swipeDown() {
        int centerX = adbConfig.getScreenWidth() / 2;
        executeCommand(String.format("shell input swipe %d 500 %d 1500 300", centerX, centerX));
        log.debug("下滑回到上一个视频");
    }

    /**
     * 双击点赞
     */
    public void doubleTapLike() {
        int centerX = adbConfig.getScreenWidth() / 2;
        int centerY = adbConfig.getScreenHeight() / 2;
        executeCommand(String.format("shell input tap %d %d", centerX, centerY));
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {}
        executeCommand(String.format("shell input tap %d %d", centerX, centerY));
        log.debug("双击点赞");
    }

    /**
     * 点击右侧点赞按钮
     */
    public void tapLikeButton() {
        tap(adbConfig.getLikeButtonX(), adbConfig.getLikeButtonY());
        log.debug("点击点赞按钮");
    }

    /**
     * 截取屏幕并保存到本地
     */
    public String captureScreen(String savePath) {
        try {
            File saveFile = new File(savePath);
            File parentDir = saveFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            if (saveFile.exists()) {
                saveFile.delete();
            }

            // 在设备上截图
            executeCommand("shell screencap -p /sdcard/screen_temp.png");
            Thread.sleep(500);

            // 拉取到本地
            executeCommand(String.format("pull /sdcard/screen_temp.png \"%s\"", savePath));
            Thread.sleep(300);

            // 清理设备上的临时文件
            executeCommand("shell rm /sdcard/screen_temp.png");

            if (saveFile.exists() && saveFile.length() > 0) {
                log.info("截图保存: {}, 大小: {} bytes", savePath, saveFile.length());
                return savePath;
            } else {
                log.error("截图文件不存在或为空: {}", savePath);
                return null;
            }
        } catch (Exception e) {
            log.error("截图失败", e);
            return null;
        }
    }

    /**
     * 检查设备是否连接
     */
    public boolean isDeviceConnected() {
        try {
            String fullCommand = String.format("%s devices", adbConfig.getPath());
            Process process = Runtime.getRuntime().exec(fullCommand);
            process.waitFor(5, TimeUnit.SECONDS);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(adbConfig.getDeviceId()) && line.contains("device")) {
                    log.info("设备已连接: {}", adbConfig.getDeviceId());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("检查设备连接失败", e);
        }
        log.warn("设备未连接: {}", adbConfig.getDeviceId());
        return false;
    }

    /**
     * 获取屏幕分辨率
     */
    public int[] getScreenSize() {
        String output = executeCommand("shell wm size");
        try {
            String[] parts = output.split(":")[1].trim().split("x");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (Exception e) {
            log.error("获取屏幕分辨率失败，使用默认值");
            return new int[]{adbConfig.getScreenWidth(), adbConfig.getScreenHeight()};
        }
    }

    /**
     * 返回键
     */
    public void pressBack() {
        executeCommand("shell input keyevent 4");
    }

    /**
     * Home键
     */
    public void pressHome() {
        executeCommand("shell input keyevent 3");
    }
}
