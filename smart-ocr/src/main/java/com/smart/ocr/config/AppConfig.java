package com.smart.ocr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    /**
     * 截图热键
     */
    private String screenshotHotkey = "F4";

    /**
     * 是否开机自启
     */
    private boolean autoStart = false;

    /**
     * 是否启动时最小化
     */
    private boolean startMinimized = false;

    /**
     * 历史记录保留天数
     */
    private int historyRetentionDays = 30;

    /**
     * 最大历史记录数
     */
    private int maxHistoryCount = 1000;

    /**
     * 是否自动复制识别结果到剪贴板
     */
    private boolean autoCopyResult = true;

    /**
     * 截图后是否自动识别
     */
    private boolean autoRecognize = true;
}
