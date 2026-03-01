package com.smart.ocr.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.smart.ocr.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * AI服务 - 基于通义千问
 * 提供文本优化、翻译、摘要等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiConfig aiConfig;

    /**
     * 优化OCR识别结果
     * 修正错别字、优化排版、补全标点
     */
    public String optimizeText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return rawText;
        }

        String systemPrompt = """
            你是一个专业的文本校对助手。请对OCR识别的文本进行优化处理：
            1. 修正明显的错别字和识别错误
            2. 补全缺失的标点符号
            3. 修正异常的空格和换行
            4. 保持原文的段落结构
            5. 不要添加原文没有的内容
            6. 直接输出优化后的文本，不要加任何解释
            """;

        return callAi(systemPrompt, rawText, aiConfig.getModel());
    }

    /**
     * 翻译文本
     */
    public String translate(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String systemPrompt = String.format("""
            你是一个专业的翻译助手。请将以下文本翻译成%s：
            1. 保持原文的语义和语气
            2. 翻译要准确、自然、流畅
            3. 专业术语要准确翻译
            4. 直接输出翻译结果，不要加任何解释或原文
            """, targetLang);

        return callAi(systemPrompt, text, aiConfig.getModel());
    }

    /**
     * 翻译为默认目标语言
     */
    public String translate(String text) {
        return translate(text, aiConfig.getTranslateTargetLang());
    }

    /**
     * 生成文本摘要
     */
    public String summarize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String systemPrompt = """
            你是一个专业的文本摘要助手。请对以下文本生成简洁的摘要：
            1. 提取核心观点和关键信息
            2. 摘要要简洁、准确、完整
            3. 控制在原文长度的20%-30%
            4. 直接输出摘要内容，不要加任何解释
            """;

        return callAi(systemPrompt, text, aiConfig.getFastModel());
    }

    /**
     * 提取关键词
     */
    public String extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String systemPrompt = """
            你是一个专业的文本分析助手。请从以下文本中提取关键词：
            1. 提取5-10个最重要的关键词
            2. 关键词用逗号分隔
            3. 按重要性排序
            4. 直接输出关键词列表，不要加任何解释
            """;

        return callAi(systemPrompt, text, aiConfig.getFastModel());
    }

    /**
     * 格式化代码
     */
    public String formatCode(String code, String language) {
        if (code == null || code.trim().isEmpty()) {
            return code;
        }

        String systemPrompt = String.format("""
            你是一个专业的代码格式化助手。请格式化以下%s代码：
            1. 使用标准的缩进和换行
            2. 保持代码逻辑不变
            3. 直接输出格式化后的代码，不要加任何解释
            4. 不要添加代码块标记
            """, language != null ? language : "");

        return callAi(systemPrompt, code, aiConfig.getFastModel());
    }

    /**
     * 异步优化文本
     */
    @Async
    public CompletableFuture<String> optimizeTextAsync(String rawText) {
        return CompletableFuture.completedFuture(optimizeText(rawText));
    }

    /**
     * 异步翻译文本
     */
    @Async
    public CompletableFuture<String> translateAsync(String text, String targetLang) {
        return CompletableFuture.completedFuture(translate(text, targetLang));
    }

    /**
     * 调用AI模型
     */
    private String callAi(String systemPrompt, String userContent, String model) {
        try {
            if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isEmpty()) {
                log.warn("AI API Key未配置，跳过AI处理");
                return userContent;
            }

            Generation generation = new Generation();

            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(systemPrompt)
                    .build();

            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(userContent)
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(aiConfig.getApiKey())
                    .model(model)
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .maxTokens(aiConfig.getMaxTokens())
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();

            GenerationResult result = generation.call(param);
            String response = result.getOutput().getChoices().get(0).getMessage().getContent();

            log.info("AI处理完成，输入长度: {}, 输出长度: {}", userContent.length(), response.length());
            return response;

        } catch (Exception e) {
            log.error("AI调用失败", e);
            return userContent; // 失败时返回原文
        }
    }

    /**
     * 检测AI服务是否可用
     */
    public boolean isAvailable() {
        return aiConfig.getApiKey() != null && !aiConfig.getApiKey().isEmpty();
    }
}
