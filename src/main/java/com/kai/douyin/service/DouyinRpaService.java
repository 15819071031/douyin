package com.kai.douyin.service;

import com.kai.douyin.dal.dto.CommentInfo;
import com.kai.douyin.dal.dto.CommentWithPosition;
import com.kai.douyin.utils.AdbCommandUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinRpaService {

    private final AdbCommandUtil adbCommand;
    private final QwenAiService qwenAiService;
    private final CommentTaskService commentTaskService;
    private final OcrService ocrService;
    private final QwenVisionService qwenVisionService;

    @Value("${douyin.comment.min-count:1}")
    private int minCommentCount;

    @Value("${douyin.comment.max-count:5}")
    private int maxCommentCount;

    @Value("${douyin.comment.interval-ms:3000}")
    private long intervalMs;

    // 报告区域坐标（根据实际设备调整）
    private static final int COMMENT_ICON_X = 1000;
    private static final int COMMENT_ICON_Y = 1570;
    private static final int COMMENT_INPUT_X = 170;
    private static final int COMMENT_INPUT_Y = 2006;
    private static final int SEND_BTN_X = 966;
    private static final int SEND_BTN_Y = 2180;
    
    // 评论项点击区域
    private static final int COMMENT_ITEM_X = 300;
    
    // "回复"按钮的X坐标（评论右侧的回复文字）
    private static final int REPLY_BTN_X = 200;

    public void swipeToNextVideo() {
        log.info("滑动到下一个视频");
        adbCommand.swipeUp();
        sleep(intervalMs);
    }

    public void openCommentSection() {
        log.info("打开评论区");
        adbCommand.tap(COMMENT_ICON_X, COMMENT_ICON_Y);
        sleep(2000); // 等待评论区加载
    }

    public void closeCommentSection() {
        log.info("关闭评论区");
        adbCommand.pressBack();
        sleep(500);
    }

    /**
     * 通过OCR识别获取当前屏幕的评论列表
     */
    public List<CommentInfo> getComments() {
        log.info("开始通过OCR识别评论...");
        
        // 使用OCR服务截图并识别评论
        List<CommentInfo> comments = ocrService.captureAndRecognizeComments();
        
        // 限制评论数量
        Random random = new Random();
        int targetCount = random.nextInt(maxCommentCount - minCommentCount + 1) + minCommentCount;
        
        if (comments.size() > targetCount) {
            comments = comments.subList(0, targetCount);
        }
        
        log.info("获取到 {} 条评论（目标: {}）", comments.size(), targetCount);
        
        return comments;
    }

    /**
     * 滑动评论区获取更多评论
     */
    public List<CommentInfo> scrollAndGetMoreComments(int scrollTimes) {
        List<CommentInfo> allComments = new ArrayList<>();
        
        // 获取当前屏幕评论
        allComments.addAll(getComments());
        
        // 滑动获取更多
        for (int i = 0; i < scrollTimes; i++) {
            log.info("滑动评论区 ({}/{})", i + 1, scrollTimes);
            adbCommand.scrollCommentArea();
            sleep(1500);
            
            List<CommentInfo> newComments = ocrService.captureAndRecognizeComments();
            
            // 过滤重复评论
            for (CommentInfo newComment : newComments) {
                boolean isDuplicate = allComments.stream()
                        .anyMatch(c -> c.getContent().equals(newComment.getContent()));
                if (!isDuplicate) {
                    allComments.add(newComment);
                }
            }
        }
        
        log.info("滑动后共获取 {} 条评论", allComments.size());
        return allComments;
    }

    /**
     * 回复评论
     */
    public void replyToComment(CommentInfo comment, String replyText) {
        log.info("回复评论: [{}] {} -> {}", comment.getUserName(), comment.getContent(), replyText);
        
        // 如果有评论位置，先点击该评论来回复
        if (comment.getPositionY() != null && comment.getPositionY() > 0) {
            adbCommand.tap(COMMENT_ITEM_X, comment.getPositionY());
            sleep(500);
        } else {
            // 否则点击输入框
            adbCommand.tap(COMMENT_INPUT_X, COMMENT_INPUT_Y);
            sleep(500);
        }
        
        // 输入回复内容（中文需要特殊处理）
        adbCommand.inputChineseText(replyText);
        sleep(500);
        
        // 点击发送
        adbCommand.tap(SEND_BTN_X, SEND_BTN_Y);
        sleep(1000);
        
        log.info("评论回复已发送");
    }

    /**
     * 处理当前视频的评论
     */
    public void processCurrentVideo() {
        try {
            // 打开评论区
            openCommentSection();
            
            // OCR识别获取评论
            List<CommentInfo> comments = getComments();
            
            if (comments.isEmpty()) {
                log.info("未识别到评论，跳过当前视频");
                closeCommentSection();
                return;
            }
            
            // 对每条评论进行AI回复
            for (CommentInfo comment : comments) {
                try {
                    // 调用AI生成回复
                    String reply = qwenAiService.generateReply(comment.getContent());
                    
                    if (reply != null && !reply.isEmpty()) {
                        // 发送回复
                        replyToComment(comment, reply);
                        comment.setReplyContent(reply);
                        
                        // 保存成功日志
                        commentTaskService.saveLog(comment, 1, null);
                        log.info("评论回复成功: {} -> {}", comment.getContent(), reply);
                    } else {
                        // AI生成失败
                        commentTaskService.saveLog(comment, 2, "AI生成回复失败");
                        log.warn("AI生成回复失败: {}", comment.getContent());
                    }
                    
                    // 回复间隔，避免操作过快
                    sleep(intervalMs);
                    
                } catch (Exception e) {
                    log.error("处理评论失败: {}", comment.getContent(), e);
                    commentTaskService.saveLog(comment, 2, e.getMessage());
                }
            }
            
            // 关闭评论区
            closeCommentSection();
            
        } catch (Exception e) {
            log.error("处理当前视频失败", e);
        }
    }

    /**
     * 执行批量视频评论任务
     */
    public void runTask(int videoCount) {
        log.info("开始执行抖音评论任务，目标视频数: {}", videoCount);
        
        // 检查设备连接
        if (!adbCommand.isDeviceConnected()) {
            log.error("ADB设备未连接，任务终止");
            return;
        }
        
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < videoCount; i++) {
            try {
                log.info("\n========== 处理第 {}/{} 个视频 ==========", i + 1, videoCount);
                
                processCurrentVideo();
                successCount++;
                
                // 滑到下一个视频
                if (i < videoCount - 1) {
                    swipeToNextVideo();
                }
                
            } catch (Exception e) {
                log.error("处理视频失败", e);
                failCount++;
                
                // 尝试恢复
                try {
                    closeCommentSection();
                    swipeToNextVideo();
                } catch (Exception ex) {
                    log.error("恢复失败", ex);
                }
            }
        }
        
        log.info("\n========== 抖音评论任务执行完成 ==========");
        log.info("成功: {} 个视频, 失败: {} 个视频", successCount, failCount);
    }

    /**
     * 单独测试OCR识别功能
     */
    public List<CommentInfo> testOcrRecognition() {
        log.info("测试OCR识别功能...");
        return ocrService.captureAndRecognizeComments();
    }
    
    // ==================== AI视觉识别方法 ====================
    
    /**
     * 使用AI视觉模型识别评论（新方法）
     */
    public List<CommentWithPosition> getCommentsWithAI() {
        log.info("开始通过AI视觉模型识别评论...");
        return qwenVisionService.captureAndRecognizeWithAI();
    }
    
    /**
     * 使用AI视觉模型识别评论并生成回复
     */
    public List<CommentWithPosition> getCommentsWithAIAndReplies() {
        log.info("通过AI识别评论并生成回复...");
        return qwenVisionService.recognizeAndGenerateReplies();
    }
    
    /**
     * 根据位置点击评论并回复
     * 流程：点击"回复"按钮 -> 点击输入框 -> 输入内容 -> 点击发送
     */
    public void replyToCommentByPosition(CommentWithPosition comment) {
        if (comment.getAiReply() == null || comment.getAiReply().isEmpty()) {
            log.warn("回复内容为空，跳过");
            return;
        }
        
        log.info("准备回复评论: [{}] {} -> {}", 
                comment.getUserName(), comment.getContent(), comment.getAiReply());
        
        // 1. 点击"回复"按钮（使用AI识别的坐标）
        int replyBtnX = comment.getReplyBtnX() != null ? comment.getReplyBtnX() : REPLY_BTN_X;
        int replyBtnY = comment.getReplyBtnY() != null ? comment.getReplyBtnY() : (comment.getPositionY() != null ? comment.getPositionY() + 50 : 800);
        
        adbCommand.tap(replyBtnX, replyBtnY);
        log.info("点击回复按钮: ({}, {})", replyBtnX, replyBtnY);
        sleep(1000); // 等待输入框弹出
        
        // 2. 点击输入框区域（确保获得焦点）
        adbCommand.tap(COMMENT_INPUT_X, COMMENT_INPUT_Y);
        log.info("点击输入框: ({}, {})", COMMENT_INPUT_X, COMMENT_INPUT_Y);
        sleep(500);
        
        // 3. 输入回复内容
        log.info("正在输入回复内容: {}", comment.getAiReply());
        adbCommand.inputChineseText(comment.getAiReply());
        sleep(800); // 等待输入完成
        
        // 4. 点击发送按钮
        adbCommand.tap(SEND_BTN_X, SEND_BTN_Y);
        log.info("点击发送按钮: ({}, {})", SEND_BTN_X, SEND_BTN_Y);
        sleep(1000); // 等待发送完成
        
        log.info("回复已发送成功");
    }
    
    /**
     * 使用AI视觉方式处理当前视频
     */
    public void processCurrentVideoWithAI() {
        try {
            // 打开评论区
            openCommentSection();
            
            // AI识别评论并生成回复
            List<CommentWithPosition> comments = getCommentsWithAIAndReplies();
            
            if (comments.isEmpty()) {
                log.info("未AI识别到评论，跳过当前视频");
                closeCommentSection();
                return;
            }
            
            log.info("AI识别到 {} 条评论", comments.size());
            
            // 限制回复数量
            Random random = new Random();
            int targetCount = Math.min(comments.size(), 
                    random.nextInt(maxCommentCount - minCommentCount + 1) + minCommentCount);
            
            // 对每条评论进行回复
            for (int i = 0; i < targetCount; i++) {
                CommentWithPosition comment = comments.get(i);
                
                if (Boolean.TRUE.equals(comment.getShouldReply()) && comment.getAiReply() != null) {
                    try {
                        replyToCommentByPosition(comment);
                        
                        // 保存日志
                        CommentInfo info = new CommentInfo();
                        info.setUserName(comment.getUserName());
                        info.setContent(comment.getContent());
                        info.setReplyContent(comment.getAiReply());
                        commentTaskService.saveLog(info, 1, null);
                        
                        // 回复间隔
                        sleep(intervalMs);
                        
                    } catch (Exception e) {
                        log.error("回复评论失败: {}", comment.getContent(), e);
                    }
                }
            }
            
            // 关闭评论区
            closeCommentSection();
            
        } catch (Exception e) {
            log.error("AI方式处理视频失败", e);
        }
    }
    
    /**
     * 使用AI视觉方式执行批量任务
     */
    public void runTaskWithAI(int videoCount) {
        log.info("开始执行AI视觉评论任务，目标视频数: {}", videoCount);
        
        if (!adbCommand.isDeviceConnected()) {
            log.error("ADB设备未连接，任务终止");
            return;
        }
        
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < videoCount; i++) {
            try {
                log.info("\n===== [AI模式] 处理第 {}/{} 个视频 =====", i + 1, videoCount);
                
                processCurrentVideoWithAI();
                successCount++;
                
                if (i < videoCount - 1) {
                    swipeToNextVideo();
                }
                
            } catch (Exception e) {
                log.error("处理视频失败", e);
                failCount++;
                
                try {
                    closeCommentSection();
                    swipeToNextVideo();
                } catch (Exception ex) {
                    log.error("恢复失败", ex);
                }
            }
        }
        
        log.info("\n===== AI视觉评论任务完成 =====");
        log.info("成功: {} 个, 失败: {} 个", successCount, failCount);
    }

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}