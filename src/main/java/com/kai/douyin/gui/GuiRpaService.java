package com.kai.douyin.gui;

import com.kai.douyin.dal.dto.CommentInfo;
import com.kai.douyin.dal.dto.CommentWithPosition;
import com.kai.douyin.service.OcrService;
import com.kai.douyin.service.QwenAiService;
import com.kai.douyin.service.QwenVisionService;
import com.kai.douyin.utils.AdbCommandUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * GUI专用RPA服务
 * 使用界面配置的动态坐标执行操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuiRpaService {

    private final AdbCommandUtil adbCommand;
    private final GuiCoordinateConfig config;
    private final OcrService ocrService;
    private final QwenVisionService qwenVisionService;
    private final QwenAiService qwenAiService;

    private volatile boolean running = false;
    private Consumer<String> logCallback;

    /**
     * 设置日志回调
     */
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * 停止当前任务
     */
    public void stopTask() {
        running = false;
        logInfo("任务停止信号已发送");
    }

    /**
     * 检查设备连接
     */
    public boolean isDeviceConnected() {
        return adbCommand.isDeviceConnected();
    }

    /**
     * 获取屏幕尺寸
     */
    public int[] getScreenSize() {
        return adbCommand.getScreenSize();
    }

    // ==================== 基础操作 ====================

    /**
     * 打开评论区（使用配置的坐标）
     */
    public void openCommentSection() {
        logInfo("打开评论区: (" + config.getCommentIconX() + ", " + config.getCommentIconY() + ")");
        adbCommand.tap(config.getCommentIconX(), config.getCommentIconY());
        sleep(1500);
    }

    /**
     * 关闭评论区
     */
    public void closeCommentSection() {
        logInfo("关闭评论区");
        adbCommand.pressBack();
        sleep(500);
    }

    /**
     * 滑动到下一个视频
     */
    public void swipeToNextVideo() {
        logInfo("滑动到下一个视频");
        adbCommand.swipeUp();
        sleep(config.getIntervalMs());
    }

    /**
     * 点击关注按钮
     */
    public void followAuthor() {
        logInfo("点击关注按钮: (" + config.getFollowBtnX() + ", " + config.getFollowBtnY() + ")");
        adbCommand.tap(config.getFollowBtnX(), config.getFollowBtnY());
        sleep(800);
    }

    /**
     * 发送评论
     */
    public void postComment(String text) {
        logInfo("准备发送评论: " + text);

        // 点击输入框
        adbCommand.tap(config.getCommentInputX(), config.getCommentInputY());
        logInfo("点击输入框: (" + config.getCommentInputX() + ", " + config.getCommentInputY() + ")");
        sleep(800);

        // 输入评论内容
        adbCommand.inputChineseText(text);
        logInfo("输入评论内容: " + text);
        sleep(800);

        // 点击发送按钮
        adbCommand.tap(config.getSendBtnX(), config.getSendBtnY());
        logInfo("点击发送按钮: (" + config.getSendBtnX() + ", " + config.getSendBtnY() + ")");
        sleep(1000);

        logInfo("评论发送完成");
    }

    // ==================== OCR测试 ====================

    /**
     * 测试OCR识别
     */
    public List<CommentInfo> testOcrRecognition() {
        logInfo("测试OCR识别...");
        return ocrService.captureAndRecognizeComments();
    }

    // ==================== 互关互评任务 ====================

    /**
     * 处理当前视频（互关互评）
     */
    public void processFollowComment(String commentText) {
        try {
            logInfo("===== 开始处理当前视频 =====");

            // 1. 点击关注
            followAuthor();
            logInfo("✓ 已点击关注");

            // 2. 打开评论区
            openCommentSection();
            logInfo("✓ 已打开评论区");

            // 3. 发送评论
            postComment(commentText);
            logInfo("✓ 已发送评论: " + commentText);

            // 4. 关闭评论区
            closeCommentSection();
            logInfo("✓ 已关闭评论区");

            logInfo("===== 当前视频处理完成 =====");

        } catch (Exception e) {
            logInfo("处理当前视频失败: " + e.getMessage());
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
    public void runFollowCommentTask(int videoCount, String commentText) {
        running = true;
        logInfo("========================================");
        logInfo("开始执行互关互评任务");
        logInfo("目标视频数: " + videoCount);
        logInfo("评论内容: " + commentText);
        logInfo("========================================");

        if (!adbCommand.isDeviceConnected()) {
            logInfo("ADB设备未连接，任务终止");
            running = false;
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < videoCount && running; i++) {
            try {
                logInfo("----- 处理第 " + (i + 1) + "/" + videoCount + " 个视频 -----");

                processFollowComment(commentText);
                successCount++;

                if (i < videoCount - 1 && running) {
                    swipeToNextVideo();
                }

            } catch (Exception e) {
                logInfo("处理视频失败: " + e.getMessage());
                failCount++;

                try {
                    closeCommentSection();
                    if (running) swipeToNextVideo();
                } catch (Exception ex) {
                    logInfo("恢复失败: " + ex.getMessage());
                }
            }
        }

        logInfo("========================================");
        logInfo("互关互评任务执行完成");
        logInfo("成功: " + successCount + " 个视频");
        logInfo("失败: " + failCount + " 个视频");
        logInfo("========================================");
        running = false;
    }

    // ==================== AI评论任务 ====================

    /**
     * 处理当前视频（AI评论）
     */
    public void processAiComment() {
        try {
            openCommentSection();

            List<CommentWithPosition> comments = qwenVisionService.recognizeAndGenerateReplies();

            if (comments.isEmpty()) {
                logInfo("未识别到评论，跳过当前视频");
                closeCommentSection();
                return;
            }

            logInfo("AI识别到 " + comments.size() + " 条评论");

            Random random = new Random();
            int targetCount = Math.min(comments.size(), random.nextInt(3) + 2);

            for (int i = 0; i < targetCount; i++) {
                CommentWithPosition comment = comments.get(i);

                if (Boolean.TRUE.equals(comment.getShouldReply()) && comment.getAiReply() != null) {
                    logInfo("回复: [" + comment.getUserName() + "] " + comment.getContent() + " -> " + comment.getAiReply());

                    int replyBtnX = comment.getReplyBtnX() != null ? comment.getReplyBtnX() : 200;
                    int replyBtnY = comment.getReplyBtnY() != null ? comment.getReplyBtnY() :
                            (comment.getPositionY() != null ? comment.getPositionY() + 50 : 800);

                    adbCommand.tap(replyBtnX, replyBtnY);
                    sleep(1000);

                    adbCommand.tap(config.getCommentInputX(), config.getCommentInputY());
                    sleep(500);

                    adbCommand.inputChineseText(comment.getAiReply());
                    sleep(800);

                    adbCommand.tap(config.getSendBtnX(), config.getSendBtnY());
                    sleep(1000);

                    logInfo("回复已发送");
                    sleep(config.getIntervalMs());
                }
            }

            closeCommentSection();

        } catch (Exception e) {
            logInfo("AI方式处理视频失败: " + e.getMessage());
        }
    }

    /**
     * 批量执行AI评论任务
     */
    public void runAiCommentTask(int videoCount) {
        running = true;
        logInfo("========================================");
        logInfo("开始执行AI视觉评论任务");
        logInfo("目标视频数: " + videoCount);
        logInfo("========================================");

        if (!adbCommand.isDeviceConnected()) {
            logInfo("ADB设备未连接，任务终止");
            running = false;
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < videoCount && running; i++) {
            try {
                logInfo("----- [AI模式] 处理第 " + (i + 1) + "/" + videoCount + " 个视频 -----");

                processAiComment();
                successCount++;

                if (i < videoCount - 1 && running) {
                    swipeToNextVideo();
                }

            } catch (Exception e) {
                logInfo("处理视频失败: " + e.getMessage());
                failCount++;

                try {
                    closeCommentSection();
                    if (running) swipeToNextVideo();
                } catch (Exception ex) {
                    logInfo("恢复失败: " + ex.getMessage());
                }
            }
        }

        logInfo("========================================");
        logInfo("AI视觉评论任务完成");
        logInfo("成功: " + successCount + " 个");
        logInfo("失败: " + failCount + " 个");
        logInfo("========================================");
        running = false;
    }

    // ==================== 工具方法 ====================

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logInfo(String message) {
        log.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }
}
