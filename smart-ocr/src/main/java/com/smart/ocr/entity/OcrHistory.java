package com.smart.ocr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCR识别历史记录实体
 */
@Data
@TableName("ocr_history")
public class OcrHistory {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 识别的文本内容
     */
    private String text;

    /**
     * 源图片路径
     */
    private String imagePath;

    /**
     * 识别类型：screenshot截图识别 / file文件识别 / clipboard剪贴板识别
     */
    private String type;

    /**
     * 字符数
     */
    private Integer charCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
