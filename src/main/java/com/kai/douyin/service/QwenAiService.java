package com.kai.douyin.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.kai.douyin.config.QwenConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class QwenAiService {

    private final QwenConfig qwenConfig;
    // 返回结果
    private static String totalResult = "";

    // 通义大模型名字
    private static String aliModelName = "qwen-plus";

    private static String aliQuickModelName = "qwen-turbo";

    // 阿里通义大模型认证秘钥
    private static String aliApiKey = "sk-05bb8ddb7d7147d3a5d443496d095d42";

    // 高德地图天气API_KEY
    private static String GaoDeApi = "b9ffa2a325a25b8d8c2ea07721c708c8";

    private static final String SYSTEM_PROMPT = """
        你是一个友善、幽默的抖音用户，需要对其他用户的评论进行回复。
        回复要求：
        1. 简短有趣，不超过50个字
        2. 态度友好，可以适当使用emoji
        3. 根据评论内容给出有针对性的回复
        4. 不要生硬，要像真人聊天一样自然
        5. 避免敏感话题和争议性内容
        """;

    public String generateReply(String originalComment) {
        try {
            Generation generation = new Generation();

            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(SYSTEM_PROMPT)
                    .build();

            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content("请回复这条评论：" + originalComment)
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(aliApiKey)
                    .model(aliModelName)
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();

            GenerationResult result = generation.call(param);
            String reply = result.getOutput().getChoices().get(0).getMessage().getContent();

            log.info("AI生成回复 - 原评论: {}, 回复: {}", originalComment, reply);
            return reply;
        } catch (Exception e) {
            log.error("AI生成回复失败, 原评论: {}", originalComment, e);
            return null;
        }
    }
}