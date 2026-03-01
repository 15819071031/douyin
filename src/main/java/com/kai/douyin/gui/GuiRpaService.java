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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
     * 双击点赞
     */
    public void doubleTapLike() {
        int centerX = 540; // 屏幕中心X
        int centerY = 1000; // 屏幕中心Y
        logInfo("双击点赞");
        adbCommand.tap(centerX, centerY);
        sleep(50);
        adbCommand.tap(centerX, centerY);
        sleep(500);
    }

    /**
     * 点击右侧点赞按钮
     */
    public void tapLikeButton() {
        logInfo("点击点赞按钮: (" + config.getLikeBtnX() + ", " + config.getLikeBtnY() + ")");
        adbCommand.tap(config.getLikeBtnX(), config.getLikeBtnY());
        sleep(500);
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

    // ==================== 刷视频点赞任务 ====================

    /**
     * 识别视频中的文字
     */
    public String recognizeVideoText() {
        try {
            // 截图
            String screenshotPath = adbCommand.captureScreen("./temp/video_screen.png");
            if (screenshotPath == null) {
                logInfo("截图失败");
                return "";
            }

            // 读取图片
            BufferedImage fullImage = ImageIO.read(new File(screenshotPath));
            if (fullImage == null) {
                return "";
            }

            // 裁剪视频文字区域
            int x = config.getVideoTextAreaX();
            int y = config.getVideoTextAreaY();
            int width = Math.min(config.getVideoTextAreaWidth(), fullImage.getWidth() - x);
            int height = Math.min(config.getVideoTextAreaHeight(), fullImage.getHeight() - y);

            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (width <= 0 || height <= 0) {
                logInfo("裁剪区域无效");
                return "";
            }

            BufferedImage croppedImage = fullImage.getSubimage(x, y, width, height);
            
            // 保存裁剪后的图片用于OCR
            String croppedPath = "./temp/video_text_area.png";
            ImageIO.write(croppedImage, "png", new File(croppedPath));

            // OCR识别 - 使用OcrService的方法
            var result = ocrService.captureAndRecognizeComments();
            StringBuilder sb = new StringBuilder();
            for (var comment : result) {
                sb.append(comment.getContent()).append(" ");
            }
            return sb.toString();
        } catch (Exception e) {
            logInfo("识别视频文字失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 关键词匹配
     */
    public List<String> matchKeywords(String text, List<String> keywords) {
        List<String> matched = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return matched;
        }

        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            }
        }
        return matched;
    }

    /**
     * 执行刷视频点赞任务
     * @param videoCount 视频数量
     * @param keywordsStr 关键词（逗号分隔）
     * @param progressCallback 进度回调
     */
    public void runBrowseAndLikeTask(int videoCount, String keywordsStr, Consumer<BrowseResult> progressCallback) {
        running = true;
        
        // 解析关键词
        List<String> keywords = Arrays.stream(keywordsStr.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        
        if (keywords.isEmpty()) {
            keywords = Arrays.asList("幸福", "女性", "独立", "情感");
        }

        logInfo("========================================");
        logInfo("开始执行刷视频点赞任务");
        logInfo("目标视频数: " + videoCount);
        logInfo("关键词: " + keywords);
        logInfo("========================================");

        if (!adbCommand.isDeviceConnected()) {
            logInfo("ADB设备未连接，任务终止");
            running = false;
            return;
        }

        int likedCount = 0;
        int scannedCount = 0;

        for (int i = 0; i < videoCount && running; i++) {
            scannedCount++;
            BrowseResult result = new BrowseResult();
            result.videoIndex = i + 1;
            result.totalCount = videoCount;

            try {
                logInfo("----- 处理第 " + (i + 1) + "/" + videoCount + " 个视频 -----");

                // 等待视频加载
                sleep(1500);

                // 截图并OCR识别
                String recognizedText = recognizeVideoText();
                result.recognizedText = recognizedText;
                logInfo("识别到文字: " + (recognizedText.length() > 50 ? recognizedText.substring(0, 50) + "..." : recognizedText));

                // 关键词匹配
                List<String> matchedKeywords = matchKeywords(recognizedText, keywords);
                result.matchedKeywords = matchedKeywords;

                // 如果匹配到关键词，点赞
                if (!matchedKeywords.isEmpty()) {
                    doubleTapLike();
                    result.liked = true;
                    likedCount++;
                    logInfo("✓ 匹配关键词: " + matchedKeywords + "，已点赞！");
                } else {
                    result.liked = false;
                    logInfo("✗ 未匹配关键词，跳过");
                }

                result.success = true;
                result.message = result.liked ? "匹配: " + String.join(",", matchedKeywords) : "未匹配";

            } catch (Exception e) {
                logInfo("处理视频失败: " + e.getMessage());
                result.success = false;
                result.message = "处理失败: " + e.getMessage();
            }

            // 回调进度
            if (progressCallback != null) {
                progressCallback.accept(result);
            }

            // 滑动到下一个视频
            if (i < videoCount - 1 && running) {
                swipeToNextVideo();
            }
        }

        logInfo("========================================");
        logInfo("刷视频任务完成");
        logInfo("共扫描: " + scannedCount + " 个视频");
        logInfo("共点赞: " + likedCount + " 个视频");
        logInfo("========================================");
        running = false;
    }

    /**
     * 刷视频结果
     */
    public static class BrowseResult {
        public int videoIndex;
        public int totalCount;
        public boolean success;
        public String message;
        public String recognizedText;
        public List<String> matchedKeywords;
        public boolean liked;

        public String getTextPreview() {
            if (recognizedText == null || recognizedText.isEmpty()) {
                return "(无文字)";
            }
            String cleaned = recognizedText.replaceAll("\\s+", " ").trim();
            return cleaned.length() > 30 ? cleaned.substring(0, 30) + "..." : cleaned;
        }
    }
}
