package com.kai.douyin.utils;

import com.kai.douyin.config.AdbConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdbCommandUtil {

    private final AdbConfig adbConfig;

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

    public void tap(int x, int y) {
        executeCommand(String.format("shell input tap %d %d", x, y));
    }

    /**
     * 长按操作
     */
    public void longPress(int x, int y, int durationMs) {
        executeCommand(String.format("shell input swipe %d %d %d %d %d", x, y, x, y, durationMs));
    }

    public void swipeUp() {
        executeCommand("shell input swipe 540 1500 540 500 300");
    }

    public void swipeDown() {
        executeCommand("shell input swipe 540 500 540 1500 300");
    }

    /**
     * 评论区内小幅度上滑，查看更多评论
     */
    public void scrollCommentArea() {
        executeCommand("shell input swipe 540 1600 540 1200 500");
    }

    public void inputText(String text) {
        String escapedText = text.replace(" ", "%s").replace("'", "\\'");
        executeCommand(String.format("shell input text '%s'", escapedText));
    }

    public void inputChineseText(String text) {
        // 方法1: 通过ADBKeyboard广播输入（需要安装ADBKeyboard）
        String result = executeCommand(String.format("shell am broadcast -a ADB_INPUT_TEXT --es msg '%s'", text));
        log.info("广播输入结果: {}", result);
        
        // 如果广播方式失败，尝试其他方式
        if (result == null || result.isEmpty() || result.contains("No receivers")) {
            log.warn("ADBKeyboard广播失败，尝试剪贴板方式...");
            inputChineseViaClipboard(text);
        }
    }
    
    /**
     * 通过剪贴板输入中文
     */
    public void inputChineseViaClipboard(String text) {
        try {
            // 设置剪贴板内容（使用service call方式）
            String escapedText = text.replace("'", "\\'").replace("\"", "\\\"");
            
            // 方法: 通过input text先输入一个空格触发输入框获取焦点，然后使用ime输入
            // 使用 input text 的 Unicode 方式
            StringBuilder unicodeCmd = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (c > 127) {
                    // 非 ASCII 字符，使用Unicode编码
                    unicodeCmd.append(String.format("%%u%04x", (int) c));
                } else if (c == ' ') {
                    unicodeCmd.append("%s");
                } else if (c == '\'' || c == '"' || c == '\\') {
                    unicodeCmd.append("\\").append(c);
                } else {
                    unicodeCmd.append(c);
                }
            }
            
            // 尝试使用 input text
            String inputResult = executeCommand(String.format("shell input text '%s'", unicodeCmd.toString()));
            log.info("input text结果: {}", inputResult);
            
        } catch (Exception e) {
            log.error("剪贴板输入失败", e);
        }
    }
    
    /**
     * 通过ime命令输入中文（适用于部分设备）
     */
    public void inputChineseViaIme(String text) {
        // 使用input text配合base64编码
        try {
            String base64Text = java.util.Base64.getEncoder().encodeToString(text.getBytes("UTF-8"));
            executeCommand(String.format("shell input text '%s'", base64Text));
        } catch (Exception e) {
            log.error("IME输入失败", e);
        }
    }

    public void pressBack() {
        executeCommand("shell input keyevent 4");
    }

    public void pressEnter() {
        executeCommand("shell input keyevent 66");
    }

    /**
     * 截取屏幕并保存到本地
     * @return 截图文件路径，失败返回null
     */
    public String captureScreen(String savePath) {
        try {
            // 确保目标目录存在
            File saveFile = new File(savePath);
            File parentDir = saveFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 删除旧文件
            if (saveFile.exists()) {
                saveFile.delete();
            }
            
            // 在设备上截图
            String screencapResult = executeCommand("shell screencap -p /sdcard/screen.png");
            log.debug("截图结果: {}", screencapResult);
            
            // 等待截图完成
            Thread.sleep(500);
            
            // 拉取到本地
            String pullResult = executeCommand(String.format("pull /sdcard/screen.png \"%s\"", savePath));
            log.debug("拉取结果: {}", pullResult);
            
            // 等待文件写入完成
            Thread.sleep(300);
            
            // 清理设备上的临时文件
            executeCommand("shell rm /sdcard/screen.png");
            
            // 验证文件是否存在
            if (saveFile.exists() && saveFile.length() > 0) {
                log.info("屏幕截图已保存: {}, 大小: {} bytes", savePath, saveFile.length());
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
     * 获取屏幕分辨率
     */
    public int[] getScreenSize() {
        String output = executeCommand("shell wm size");
        // 输出格式: Physical size: 1080x1920
        try {
            String[] parts = output.split(":")[1].trim().split("x");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (Exception e) {
            log.error("获取屏幕分辨率失败", e);
            return new int[]{1080, 1920}; // 默认分辨率
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
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("检查设备连接失败", e);
        }
        return false;
    }
}