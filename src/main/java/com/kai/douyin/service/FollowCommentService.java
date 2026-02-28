package com.kai.douyin.service;

import com.kai.douyin.utils.AdbCommandUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 互关互评服务
 * 功能：点关注 -> 打开评论区 -> 发送评论 -> 下一个视频
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowCommentService {

    private final AdbCommandUtil adbCommand;

    @Value("${douyin.follow-comment.interval-ms:3000}")
    private long intervalMs;

    @Value("${douyin.follow-comment.comment-text:互关互关}")
    private String defaultCommentText;

    // ==================== 坐标配置（根据设备分辨率调整）====================
    
    // 关注按钮位置（视频右侧的红色+号）
    private static final int FOLLOW_BTN_X = 993;
    private static final int FOLLOW_BTN_Y = 1066;
    
    // 评论图标位置
    private static final int COMMENT_ICON_X = 1000;
    private static final int COMMENT_ICON_Y = 1570;
    
    // 评论输入框位置
    private static final int COMMENT_INPUT_X = 170;
    private static final int COMMENT_INPUT_Y = 2006;
    
    // 发送按钮位置
    private static final int SEND_BTN_X = 966;
    private static final int SEND_BTN_Y = 2180;

    /**
     * 点击关注按钮
     */
    public void followAuthor() {
        log.info("点击关注按钮: ({}, {})", FOLLOW_BTN_X, FOLLOW_BTN_Y);
        adbCommand.tap(FOLLOW_BTN_X, FOLLOW_BTN_Y);
        sleep(800);
    }

    /**
     * 打开评论区
     */
    public void openCommentSection() {
        log.info("打开评论区: ({}, {})", COMMENT_ICON_X, COMMENT_ICON_Y);
        adbCommand.tap(COMMENT_ICON_X, COMMENT_ICON_Y);
        sleep(1500); // 等待评论区加载
    }

    /**
     * 关闭评论区
     */
    public void closeCommentSection() {
        log.info("关闭评论区");
        adbCommand.pressBack();
        sleep(500);
    }

    /**
     * 发送评论（直接在评论区发评论，不是回复某条评论）
     */
    public void postComment(String text) {
        log.info("准备发送评论: {}", text);
        
        // 1. 点击评论输入框
        adbCommand.tap(COMMENT_INPUT_X, COMMENT_INPUT_Y);
        log.info("点击输入框: ({}, {})", COMMENT_INPUT_X, COMMENT_INPUT_Y);
        sleep(800);
        
        // 2. 输入评论内容
        adbCommand.inputChineseText(text);
        log.info("输入评论内容: {}", text);
        sleep(800);
        
        // 3. 点击发送按钮
        adbCommand.tap(SEND_BTN_X, SEND_BTN_Y);
        log.info("点击发送按钮: ({}, {})", SEND_BTN_X, SEND_BTN_Y);
        sleep(1000);
        
        log.info("评论发送完成");
    }

    /**
     * 滑动到下一个视频
     */
    public void swipeToNextVideo() {
        log.info("滑动到下一个视频");
        adbCommand.swipeUp();
        sleep(intervalMs);
    }

    /**
     * 处理当前视频：关注 + 评论
     */
    public void processCurrentVideo() {
        processCurrentVideo(defaultCommentText);
    }

    /**
     * 处理当前视频：关注 + 评论（自定义评论内容）
     */
    public void processCurrentVideo(String commentText) {
        try {
            log.info("===== 开始处理当前视频 =====");
            
            // 1. 点击关注
            followAuthor();
            log.info("✓ 已点击关注");
            
            // 2. 打开评论区
            openCommentSection();
            log.info("✓ 已打开评论区");
            
            // 3. 发送评论
            postComment(commentText);
            log.info("✓ 已发送评论: {}", commentText);
            
            // 4. 关闭评论区
            closeCommentSection();
            log.info("✓ 已关闭评论区");
            
            log.info("===== 当前视频处理完成 =====\n");
            
        } catch (Exception e) {
            log.error("处理当前视频失败", e);
            // 尝试恢复
            try {
                closeCommentSection();
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    /**
     * 批量执行互关互评任务
     */
    public void runTask(int videoCount) {
        runTask(videoCount, defaultCommentText);
    }

    /**
     * 批量执行互关互评任务（自定义评论内容）
     */
    public void runTask(int videoCount, String commentText) {
        log.info("\n========================================");
        log.info("开始执行互关互评任务");
        log.info("目标视频数: {}", videoCount);
        log.info("评论内容: {}", commentText);
        log.info("========================================\n");

        // 检查设备连接
        if (!adbCommand.isDeviceConnected()) {
            log.error("ADB设备未连接，任务终止");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < videoCount; i++) {
            try {
                log.info("\n----- 处理第 {}/{} 个视频 -----", i + 1, videoCount);

                // 处理当前视频
                processCurrentVideo(commentText);
                successCount++;

                // 滑到下一个视频（最后一个不用滑）
                if (i < videoCount - 1) {
                    swipeToNextVideo();
                }

            } catch (Exception e) {
                log.error("处理视频失败", e);
                failCount++;

                // 尝试恢复并继续
                try {
                    closeCommentSection();
                    swipeToNextVideo();
                } catch (Exception ex) {
                    log.error("恢复失败", ex);
                }
            }
        }

        log.info("\n========================================");
        log.info("互关互评任务执行完成");
        log.info("成功: {} 个视频", successCount);
        log.info("失败: {} 个视频", failCount);
        log.info("========================================\n");
    }

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
