package com.kai.douyin.gui;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * GUI坐标配置管理
 * 用于保存用户在界面上设置的坐标值
 */
@Data
@Component
public class GuiCoordinateConfig {
    
    // ==================== 评论区坐标 ====================
    
    /** 评论图标X坐标 */
    private int commentIconX = 1000;
    
    /** 评论图标Y坐标 */
    private int commentIconY = 1570;
    
    /** 评论输入框X坐标 */
    private int commentInputX = 170;
    
    /** 评论输入框Y坐标 */
    private int commentInputY = 2006;
    
    /** 发送按钮X坐标 */
    private int sendBtnX = 966;
    
    /** 发送按钮Y坐标 */
    private int sendBtnY = 2180;
    
    // ==================== 关注按钮坐标 ====================
    
    /** 关注按钮X坐标 */
    private int followBtnX = 993;
    
    /** 关注按钮Y坐标 */
    private int followBtnY = 1066;
    
    // ==================== 任务配置 ====================
    
    /** 处理视频数量 */
    private int videoCount = 10;
    
    /** 评论内容 */
    private String commentText = "互关互关";
    
    /** 操作间隔（毫秒） */
    private long intervalMs = 3000;
    
    /**
     * 从字符串解析整数，失败返回默认值
     */
    public static int parseIntOrDefault(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 从字符串解析长整数，失败返回默认值
     */
    public static long parseLongOrDefault(String text, long defaultValue) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
