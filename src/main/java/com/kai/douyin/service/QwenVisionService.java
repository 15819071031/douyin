package com.kai.douyin.service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.kai.douyin.config.OcrConfig;
import com.kai.douyin.config.QwenConfig;
import com.kai.douyin.dal.dto.CommentWithPosition;
import com.kai.douyin.utils.AdbCommandUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * 基于通义千问视觉模型的评论识别服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QwenVisionService {

    private final QwenConfig qwenConfig;
    private final OcrConfig ocrConfig;
    private final AdbCommandUtil adbCommand;
    private final QwenAiService qwenAiService;

    // 视觉模型
    private static final String VISION_MODEL = "qwen-vl-max";

    // 识别评论的提示词
    private static final String RECOGNIZE_PROMPT = """
        请仔细分析这张抖音评论区的截图。
        请只识别第一条评论，不要识别其他评论。
        请提取评论的用户名、评论内容、以及"回复"按钮的位置。
        
        图片尺寸信息：请将图片宽度视为100%%，高度视为100%%
        
        抖音评论区布局说明：
        - 左侧：用户头像（圆形）
        - 中间：用户名 + 评论内容
        - 评论内容下方：时间（如"1小时前"）+ 地点 + "回复"文字按钮
        
        重要："回复"按钮的位置特征：
        - "回复"是灰色文字，位于评论内容下方
        - X坐标通常在屏幕宽度的10%%-20%%位置（在时间或地点文字右侧）
        - 不要误认为用户名或@符号的位置
        - "回复"不在头像位置，而是在评论文字下方
        
        对于每条评论，请提取以下信息：
        1. userName: 用户名
        2. content: 评论内容（完整的评论文字，不要包含时间、地点等信息）
        3. replyBtnXPercent: "回复"按钮的水平位置百分比（0=最左边, 100=最右边）
        4. replyBtnYPercent: "回复"按钮的垂直位置百分比（0=最顶部, 100=最底部）
        
        重要说明：
        - 抖音评论区每条评论下方都有一个"回复"文字按钮，通常在评论内容下方偏左位置
        - 请准确定位每条评论对应的"回复"按钮位置
        
        请按JSON格式返回，格式如下：
        {
            "comments": [
                {
                    "index": 1,
                    "userName": "用户名",
                    "content": "评论内容",
                    "replyBtnXPercent": 15,
                    "replyBtnYPercent": 25
                }
            ]
        }
        
        注意：
        - 只提取用户的评论内容，不要包含时间、地点
        - 如果评论包含表情，用文字描述代替
        - 如果是图片评论，content填写"[图片评论]"
        - 百分比均为整数，范围0-100
        - replyBtnXPercent 通常在 10-20 之间，不会小于5
        """;

    /**
     * 截图并使用AI识别评论
     */
    public List<CommentWithPosition> captureAndRecognizeWithAI() {
        try {
            // 1. 确保截图目录存在
            ensureDirectoryExists(ocrConfig.getScreenshotDir());
            
            // 2. 截取屏幕
            String screenshotPath = captureFullScreen();
            if (screenshotPath == null) {
                log.error("截图失败");
                return new ArrayList<>();
            }
            log.info("屏幕截图保存到: {}", screenshotPath);
            
            // 3. 获取屏幕尺寸
            int[] screenSize = adbCommand.getScreenSize();
            int screenWidth = screenSize[0];
            int screenHeight = screenSize[1];
            log.info("屏幕尺寸: {}x{}", screenWidth, screenHeight);
            
            // 4. 调用AI识别
            List<CommentWithPosition> comments = recognizeCommentsWithAI(screenshotPath, screenWidth, screenHeight);
            
            log.info("AI识别到 {} 条评论", comments.size());
            return comments;
            
        } catch (Exception e) {
            log.error("AI识别评论失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 截取全屏
     */
    private String captureFullScreen() {
        String fileName = "ai_screenshot_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
        String savePath = ocrConfig.getScreenshotDir() + "/" + fileName;
        return adbCommand.captureScreen(savePath);
    }

    /**
     * 确保目录存在
     */
    private void ensureDirectoryExists(String dir) {
        try {
            File file = new File(dir);
            if (!file.exists()) {
                file.mkdirs();
            }
        } catch (Exception e) {
            log.error("创建目录失败: {}", dir, e);
        }
    }

    /**
     * 调用AI视觉模型识别评论
     */
    private List<CommentWithPosition> recognizeCommentsWithAI(String imagePath, int screenWidth, int screenHeight) {
        try {
            // 将图片转为Base64
            File imageFile = new File(imagePath);
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String imageUrl = "data:image/png;base64," + base64Image;
            
            // 构建多模态消息
            MultiModalConversation conversation = new MultiModalConversation();
            
            // 创建包含图片和文字的消息内容
            List<Map<String, Object>> userContent = Arrays.asList(
                Map.of("image", imageUrl),
                Map.of("text", RECOGNIZE_PROMPT)
            );
            
            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(userContent)
                    .build();
            
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(qwenConfig.getApiKey())
                    .model(VISION_MODEL)
                    .messages(Arrays.asList(userMessage))
                    .build();
            
            log.info("调用AI视觉模型识别评论...");
            MultiModalConversationResult result = conversation.call(param);
            
            String responseText = result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
            log.info("AI返回结果: {}", responseText);
            
            // 解析AI返回的JSON
            return parseAIResponse(responseText, screenWidth, screenHeight);
            
        } catch (Exception e) {
            log.error("调用AI视觉模型失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析AI返回的JSON结果
     */
    private List<CommentWithPosition> parseAIResponse(String response, int screenWidth, int screenHeight) {
        List<CommentWithPosition> comments = new ArrayList<>();
        
        try {
            // 提取JSON部分（AI可能返回额外的文字说明）
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}");
            if (jsonStart == -1 || jsonEnd == -1) {
                log.error("AI返回结果中没有找到JSON");
                return comments;
            }
            
            String jsonStr = response.substring(jsonStart, jsonEnd + 1);
            JSONObject json = JSON.parseObject(jsonStr);
            JSONArray commentsArray = json.getJSONArray("comments");
            
            if (commentsArray == null) {
                return comments;
            }
            
            for (int i = 0; i < commentsArray.size(); i++) {
                JSONObject item = commentsArray.getJSONObject(i);
                
                CommentWithPosition comment = new CommentWithPosition();
                comment.setIndex(item.getInteger("index"));
                comment.setUserName(item.getString("userName"));
                comment.setContent(item.getString("content"));
                
                // 解析"回复"按钮位置并转换为实际像素坐标
                Integer replyBtnXPercent = item.getInteger("replyBtnXPercent");
                Integer replyBtnYPercent = item.getInteger("replyBtnYPercent");
                
                if (replyBtnXPercent != null) {
                    int replyBtnX = (int) (screenWidth * replyBtnXPercent / 100.0);
                    comment.setReplyBtnX(replyBtnX);
                }
                
                if (replyBtnYPercent != null) {
                    int replyBtnY = (int) (screenHeight * replyBtnYPercent / 100.0);
                    comment.setReplyBtnY(replyBtnY);
                }
                
                // 评论位置设置为回复按钮上方50像素
                if (comment.getReplyBtnY() != null) {
                    comment.setPositionY(comment.getReplyBtnY() - 50);
                }
                comment.setPositionX(comment.getReplyBtnX());
                
                // 过滤掉图片评论
                String content = comment.getContent();
                if (content != null && !content.contains("[图片评论]") && content.length() >= 2) {
                    comment.setShouldReply(true);
                    comments.add(comment);
                    log.info("识别到评论[{}]: {} - 回复按钮位置({}, {})", 
                            comment.getIndex(), comment.getContent(), 
                            comment.getReplyBtnX(), comment.getReplyBtnY());
                }
            }
            
        } catch (Exception e) {
            log.error("解析AI返回结果失败", e);
        }
        
        return comments;
    }

    /**
     * 识别评论并生成回复
     */
    public List<CommentWithPosition> recognizeAndGenerateReplies() {
        // 截图并使用AI识别评论
        List<CommentWithPosition> comments = captureAndRecognizeWithAI();
        
        // 为每条评论生成AI回复
        for (CommentWithPosition comment : comments) {
            if (Boolean.TRUE.equals(comment.getShouldReply())) {
                String reply = qwenAiService.generateReply(comment.getContent());
                comment.setAiReply(reply);
            }
        }
        
        return comments;
    }
}
