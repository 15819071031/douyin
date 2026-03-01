package com.smart.ocr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ADB配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "adb")
public class AdbConfig {

    /**
     * ADB可执行文件路径
     */
    private String path = "D:/adb/adb.exe";

    /**
     * 设备ID（通过 adb devices 查看）
     */
    private String deviceId = "127.0.0.1:5555";

    /**
     * 屏幕宽度
     */
    private int screenWidth = 1080;

    /**
     * 屏幕高度
     */
    private int screenHeight = 2400;

    /**
     * 点赞按钮X坐标（右侧）
     */
    private int likeButtonX = 980;

    /**
     * 点赞按钮Y坐标
     */
    private int likeButtonY = 880;

    /**
     * 视频文字识别区域 - X起点
     */
    private int videoTextAreaX = 50;

    /**
     * 视频文字识别区域 - Y起点
     */
    private int videoTextAreaY = 400;

    /**
     * 视频文字识别区域 - 宽度
     */
    private int videoTextAreaWidth = 900;

    /**
     * 视频文字识别区域 - 高度
     */
    private int videoTextAreaHeight = 1200;
}
