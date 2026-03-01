package com.smart.ocr;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SmartOCR Spring Boot主配置类
 */
@EnableAsync
@SpringBootApplication
@MapperScan("com.smart.ocr.mapper")
public class SmartOcrApplication {
}
