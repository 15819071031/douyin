package com.smart.ocr.dto;

import lombok.Data;

/**
 * OCR识别结果DTO
 */
@Data
public class OcrResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 识别的文本内容
     */
    private String text;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 源图片路径
     */
    private String sourcePath;

    /**
     * 字符数
     */
    private int charCount;

    /**
     * 行数
     */
    private int lineCount;

    /**
     * 开始时间
     */
    private long startTime;

    /**
     * 结束时间
     */
    private long endTime;

    /**
     * 处理耗时(毫秒)
     */
    private long processTime;
}
