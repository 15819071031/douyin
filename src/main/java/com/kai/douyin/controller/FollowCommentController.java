package com.kai.douyin.controller;

import com.kai.douyin.service.FollowCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 互关互评控制器
 * 功能：点关注 -> 打开评论区 -> 发送评论 -> 下一个视频
 */
@Slf4j
@RestController
@RequestMapping("/api/follow-comment")
@RequiredArgsConstructor
public class FollowCommentController {

    private final FollowCommentService followCommentService;

    /**
     * 处理当前视频（使用默认评论"互关互关"）
     * POST /api/follow-comment/process
     */
    @PostMapping("/process")
    public Map<String, Object> processCurrentVideo() {
        log.info("收到请求: 处理当前视频（互关互评）");
        followCommentService.processCurrentVideo();
        return Map.of(
                "success", true,
                "message", "当前视频处理完成"
        );
    }

    /**
     * 处理当前视频（自定义评论内容）
     * POST /api/follow-comment/process?text=互关互关
     */
    @PostMapping("/process-custom")
    public Map<String, Object> processCurrentVideoCustom(@RequestParam(defaultValue = "互关互关") String text) {
        log.info("收到请求: 处理当前视频，评论内容: {}", text);
        followCommentService.processCurrentVideo(text);
        return Map.of(
                "success", true,
                "message", "当前视频处理完成",
                "commentText", text
        );
    }

    /**
     * 启动批量互关互评任务（使用默认评论）
     * POST /api/follow-comment/start?count=10
     */
    @PostMapping("/start")
    public Map<String, Object> startTask(@RequestParam(defaultValue = "10") int count) {
        log.info("收到请求: 启动互关互评任务，目标视频数: {}", count);
        
        // 异步执行任务
        new Thread(() -> followCommentService.runTask(count)).start();
        
        return Map.of(
                "success", true,
                "message", "互关互评任务已启动",
                "videoCount", count,
                "commentText", "互关互关"
        );
    }

    /**
     * 启动批量互关互评任务（自定义评论内容）
     * POST /api/follow-comment/start-custom?count=10&text=互关互关
     */
    @PostMapping("/start-custom")
    public Map<String, Object> startTaskCustom(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "互关互关") String text) {
        log.info("收到请求: 启动互关互评任务，目标视频数: {}, 评论内容: {}", count, text);
        
        // 异步执行任务
        new Thread(() -> followCommentService.runTask(count, text)).start();
        
        return Map.of(
                "success", true,
                "message", "互关互评任务已启动",
                "videoCount", count,
                "commentText", text
        );
    }

    /**
     * 单独测试：点击关注
     * POST /api/follow-comment/test-follow
     */
    @PostMapping("/test-follow")
    public Map<String, Object> testFollow() {
        log.info("测试: 点击关注按钮");
        followCommentService.followAuthor();
        return Map.of("success", true, "action", "点击关注");
    }

    /**
     * 单独测试：打开评论区
     * POST /api/follow-comment/test-open-comment
     */
    @PostMapping("/test-open-comment")
    public Map<String, Object> testOpenComment() {
        log.info("测试: 打开评论区");
        followCommentService.openCommentSection();
        return Map.of("success", true, "action", "打开评论区");
    }

    /**
     * 单独测试：发送评论
     * POST /api/follow-comment/test-post-comment?text=互关互关
     */
    @PostMapping("/test-post-comment")
    public Map<String, Object> testPostComment(@RequestParam(defaultValue = "互关互关") String text) {
        log.info("测试: 发送评论 - {}", text);
        followCommentService.postComment(text);
        return Map.of("success", true, "action", "发送评论", "text", text);
    }

    /**
     * 单独测试：滑动到下一个视频
     * POST /api/follow-comment/test-swipe
     */
    @PostMapping("/test-swipe")
    public Map<String, Object> testSwipe() {
        log.info("测试: 滑动到下一个视频");
        followCommentService.swipeToNextVideo();
        return Map.of("success", true, "action", "滑动到下一个视频");
    }
}
