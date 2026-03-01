package com.smart.ocr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI配置类 - 通义千问配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    /**
     * 通义千问API Key
     */
    private String apiKey = "";

    /**
     * 模型名称
     */
    private String model = "qwen-plus";

    /**
     * 快速模型（用于简单任务）
     */
    private String fastModel = "qwen-turbo";

    /**
     * 请求超时时间（秒）
     */
    private int timeout = 30;

    /**
     * 最大Token数
     */
    private int maxTokens = 2000;

    /**
     * 翻译目标语言
     */
    private String translateTargetLang = "中文";
}
