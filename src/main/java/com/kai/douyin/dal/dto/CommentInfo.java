package com.kai.douyin.dal.dto;

import lombok.Data;

@Data
public class CommentInfo {
    /**
     * 评论用户名
     */
    private String userName;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 点赞数
     */
    private String likeCount;
    
    /**
     * AI生成的回复内容
     */
    private String replyContent;
    
    /**
     * 评论在屏幕上的Y坐标（用于点击回复）
     */
    private Integer positionY;
    
    /**
     * 评论来源：ocr/accessibility
     */
    private String source;
    
    /**
     * OCR识别置信度
     */
    private Double confidence;
}