package com.kai.douyin.controller;

import com.kai.douyin.dal.dto.CommentInfo;
import com.kai.douyin.dal.dto.CommentWithPosition;
import com.kai.douyin.service.DouyinRpaService;
import com.kai.douyin.service.OcrService;
import com.kai.douyin.service.QwenVisionService;
import com.kai.douyin.utils.AdbCommandUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentTaskController {

    private final DouyinRpaService douyinRpaService;
    private final OcrService ocrService;
    private final AdbCommandUtil adbCommand;
    private final QwenVisionService qwenVisionService;

    /**
     * 启动批量评论任务
     */
    @PostMapping("/start")
    public Map<String, Object> startTask(@RequestParam(defaultValue = "10") Integer videoCount) {
        new Thread(() -> douyinRpaService.runTask(videoCount)).start();
        return Map.of("success", true, "message", "任务已启动，目标视频数: " + videoCount);
    }

    /**
     * 滑动到下一个视频
     */
    @PostMapping("/swipe")
    public Map<String, Object> swipeNext() {
        douyinRpaService.swipeToNextVideo();
        return Map.of("success", true);
    }

    /**
     * 处理当前视频的评论
     */
    @PostMapping("/process")
    public Map<String, Object> processCurrentVideo() {
        douyinRpaService.processCurrentVideo();
        return Map.of("success", true);
    }

    /**
     * 测试OCR识别 - 截图并识别评论
     */
    @GetMapping("/test-ocr")
    public Map<String, Object> testOcrRecognition() {
        List<CommentInfo> comments = douyinRpaService.testOcrRecognition();
        return Map.of(
                "success", true,
                "count", comments.size(),
                "comments", comments
        );
    }

    /**
     * 获取当前屏幕评论（不发送回复）
     */
    @GetMapping("/get-comments")
    public Map<String, Object> getCurrentComments() {
        douyinRpaService.openCommentSection();
        List<CommentInfo> comments = ocrService.captureAndRecognizeComments();
        douyinRpaService.closeCommentSection();
        return Map.of(
                "success", true,
                "count", comments.size(),
                "comments", comments
        );
    }

    /**
     * 检查设备连接状态
     */
    @GetMapping("/device-status")
    public Map<String, Object> checkDeviceStatus() {
        boolean connected = adbCommand.isDeviceConnected();
        int[] screenSize = adbCommand.getScreenSize();
        return Map.of(
                "connected", connected,
                "screenWidth", screenSize[0],
                "screenHeight", screenSize[1]
        );
    }

    /**
     * 打开评论区
     */
    @PostMapping("/open-comments")
    public Map<String, Object> openComments() {
        douyinRpaService.openCommentSection();
        return Map.of("success", true);
    }

    /**
     * 关闭评论区
     */
    @PostMapping("/close-comments")
    public Map<String, Object> closeComments() {
        douyinRpaService.closeCommentSection();
        return Map.of("success", true);
    }
    
    // ==================== AI视觉识别接口 ====================
    
    /**
     * 使用AI视觉模型识别评论（推荐，没有打开评论区）
     */
    @GetMapping("/test-ai")
    public Map<String, Object> testAIRecognition() {
        List<CommentWithPosition> comments = qwenVisionService.captureAndRecognizeWithAI();
        return Map.of(
                "success", true,
                "method", "AI视觉识别",
                "count", comments.size(),
                "comments", comments
        );
    }
    
    /**
     * AI识别评论并生成回复
     */
    @GetMapping("/ai-with-replies")
    public Map<String, Object> getCommentsWithAIReplies() {
        douyinRpaService.openCommentSection();
        List<CommentWithPosition> comments = qwenVisionService.recognizeAndGenerateReplies();
        douyinRpaService.closeCommentSection();
        return Map.of(
                "success", true,
                "method", "AI视觉识别+AI回复",
                "count", comments.size(),
                "comments", comments
        );
    }
    
    /**
     * 使用AI方式处理当前视频
     */
    @PostMapping("/process-ai")
    public Map<String, Object> processCurrentVideoWithAI() {
        douyinRpaService.processCurrentVideoWithAI();
        return Map.of("success", true, "method", "AI视觉模式");
    }
    
    /**
     * 启动AI方式批量任务
     */
    @PostMapping("/start-ai")
    public Map<String, Object> startTaskWithAI(@RequestParam(defaultValue = "10") Integer videoCount) {
        new Thread(() -> douyinRpaService.runTaskWithAI(videoCount)).start();
        return Map.of("success", true, "message", "AI视觉任务已启动，目标视频数: " + videoCount);
    }
}