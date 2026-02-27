package com.kai.douyin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ocr")
public class OcrConfig {
    
    /**
     * OCR引擎类型: tesseract 或 paddleocr
     */
    private String engine = "tesseract";
    
    /**
     * Tesseract安装目录（包含tesseract.exe）
     */
    private String tesseractPath = "D:/Tesseract-OCR";
    
    /**
     * Tesseract数据目录路径（包含训练数据）
     */
    private String tessDataPath = "D:/Tesseract-OCR/tessdata";
    
    /**
     * 识别语言，chi_sim=简体中文，eng=英文
     */
    private String language = "chi_sim+eng";
    
    /**
     * PaddleOCR服务地址（如果使用PaddleOCR）
     */
    private String paddleOcrUrl = "http://localhost:8866/ocr";
    
    /**
     * 截图保存目录
     */
    private String screenshotDir = "D:/douyin_screenshots";
    
    /**
     * 评论区域裁剪配置 - 起始X坐标
     */
    private int commentAreaX = 0;
    
    /**
     * 评论区域裁剪配置 - 起始Y坐标
     */
    private int commentAreaY = 400;
    
    /**
     * 评论区域裁剪配置 - 宽度
     */
    private int commentAreaWidth = 1080;
    
    /**
     * 评论区域裁剪配置 - 高度
     */
    private int commentAreaHeight = 1800;
}
