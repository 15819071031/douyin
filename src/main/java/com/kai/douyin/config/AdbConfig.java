package com.kai.douyin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "adb")
public class AdbConfig {
    private String path;
    private String deviceId;
}