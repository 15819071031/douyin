package com.smart.ocr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OCR配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "ocr")
public class OcrConfig {

    /**
     * OCR引擎类型：tesseract / paddleocr
     */
    private String engine = "tesseract";

    /**
     * Tesseract安装路径
     */
    private String tesseractPath = "C:/Program Files/Tesseract-OCR";

    /**
     * Tesseract数据文件路径
     */
    private String tessdataPath = "C:/Program Files/Tesseract-OCR/tessdata";

    /**
     * PaddleOCR服务URL
     */
    private String paddleOcrUrl = "http://localhost:8866/ocr";

    /**
     * 识别语言
     */
    private String language = "chi_sim+eng";

    /**
     * 临时文件目录
     */
    private String tempDir = "./temp/ocr";

    /**
     * 图像预处理-是否启用灰度
     */
    private boolean enableGrayscale = true;

    /**
     * 图像预处理-是否启用二值化
     */
    private boolean enableBinarize = true;

    /**
     * 二值化阈值
     */
    private int binarizeThreshold = 180;

    /**
     * 是否启用去噪
     */
    private boolean enableDenoise = true;
}
