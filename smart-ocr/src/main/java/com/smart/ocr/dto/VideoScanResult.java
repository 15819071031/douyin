package com.smart.ocr.dto;

import lombok.Data;

import java.util.List;

/**
 * 视频扫描结果DTO
 */
@Data
public class VideoScanResult {

    /**
     * 视频序号
     */
    private int videoIndex;

    /**
     * 总视频数
     */
    private int totalCount;

    /**
     * 是否成功处理
     */
    private boolean success;

    /**
     * 处理消息
     */
    private String message;

    /**
     * 识别到的文字
     */
    private String recognizedText;

    /**
     * 匹配到的关键词
     */
    private List<String> matchedKeywords;

    /**
     * 是否已点赞
     */
    private boolean liked;

    /**
     * 截图路径
     */
    private String screenshotPath;

    /**
     * 获取简短的文字预览（前50字符）
     */
    public String getTextPreview() {
        if (recognizedText == null || recognizedText.isEmpty()) {
            return "(无文字)";
        }
        String cleaned = recognizedText.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 50) {
            return cleaned.substring(0, 50) + "...";
        }
        return cleaned;
    }
}
