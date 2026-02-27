package com.kai.douyin.dal.dto;

import lombok.Data;

/**
 * 带位置信息的评论
 */
@Data
public class CommentWithPosition {
    
    /**
     * 评论序号（从上到下）
     */
    private Integer index;
    
    /**
     * 用户名
     */
    private String userName;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 评论在屏幕上的Y坐标
     */
    private Integer positionY;
    
    /**
     * 评论在屏幕上的X坐标
     */
    private Integer positionX;
    
    /**
     * "回复"按钮的X坐标
     */
    private Integer replyBtnX;
    
    /**
     * "回复"按钮的Y坐标
     */
    private Integer replyBtnY;
    
    /**
     * 点赞数
     */
    private String likeCount;
    
    /**
     * AI生成的回复
     */
    private String aiReply;
    
    /**
     * 是否需要回复（可根据内容判断）
     */
    private Boolean shouldReply;
}
