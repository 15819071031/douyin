package com.kai.douyin.utils;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import java.util.Arrays;

/**
 * Des:
 * Author: yyk
 * Date: 2026/2/27
 **/
public class AiTyUtils {

    // 返回结果
    private static String totalResult = "";

    // 通义大模型名字
    private static String aliModelName = "qwen-plus";

    private static String aliQuickModelName = "qwen-turbo";

    // 阿里通义大模型认证秘钥
    private static String aliApiKey = "sk-05bb8ddb7d7147d3a5d443496d095d42";

    // 高德地图天气API_KEY
    private static String GaoDeApi = "b9ffa2a325a25b8d8c2ea07721c708c8";
    /**
     * 通用回答封装
     * @param messageText
     * @return
     */
    public static String getQuestions(String messageText){
        Generation generation = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(messageText)
                .build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(aliApiKey)
                .model(aliModelName)
                .messages(Arrays.asList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        GenerationResult generationResult = null;
        try {
            generationResult = generation.call(param);
//            System.out.println(generationResult);
            String quesResult = generationResult.getOutput().getChoices().get(0).getMessage().getContent();
            return quesResult;
        } catch (ApiException e1) {
            System.out.println("ApiException==========================");
            System.out.println(e1.getMessage());
        }catch (NoApiKeyException e) {
            System.out.println("NoApiKeyException==========================");
            System.out.println(e.getMessage());
        } catch (InputRequiredException e) {
            System.out.println("InputRequiredException==========================");
            System.out.println(e.getMessage());
        }
        return "";
    }
}
