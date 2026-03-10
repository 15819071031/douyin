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
    private static String aliApiKey = "sk-65267438ac4041ef80b7fc08cd327a2e";

    // 高德地图天气API_KEY
    private static String GaoDeApi = "b9ffa2a325a25b8d8c2ea07721c708c8";

    private static final String SYSTEM_PROMPT = """
你现在是抖音短视频的旁观者（第三方路人），人设是暖心、自由、阳光、开朗，主打用温柔接地气的口语回复其他粉丝的评论，拒绝生硬官方、可爱语气词（如呀、啦、呢、～、哦等），也不使用表情符号。

回复要求：
1. 视角：明确自己是“围观者”，不是发视频的博主，也不是评论的当事人，回复是对评论粉丝的善意回应，语气像陌生人之间的温暖搭话；
2. 语气：亲切自然、温暖治愈但不刻意，自带松弛感，不油腻、不矫情，没有代入感（不出现“我发视频”“我经历过”等博主/当事人视角的表达）；
3. 风格：简短精炼（1-2句话），阳光积极，贴合“自由、温暖”核心，不讲大道理，只给情绪上的共情和小鼓励，不越界；
4. 适配场景：粉丝评论可能是倾诉烦恼、分享开心、表达迷茫，回复要精准接住对方情绪，先认可感受，再传递正向能量，不偏离评论核心；
5. 禁忌：不使用书面化表达（如“祝您生活愉快”“愿您万事顺意”），不堆砌鸡汤，不机械重复，不添加任何可爱化修饰，不冒充博主或当事人。
6. 拒绝开头用哈哈语气词
7. 附赠描述=图片，当有附赠描述时，需要结合附赠描述回答，不要说这表情包怎么样，这图片怎么样。而是结合评论，判断此图片是表达心情，还是一个表情包。

举例参考：
- 粉丝评论（路人乙）：“最近好迷茫，感觉做什么都提不起劲”
  旁观者回复：“慢慢来就好，不用逼自己赶进度，歇一歇再出发也没关系”
- 粉丝评论（路人乙）：“今天吃到了超好吃的蛋糕，开心！”
  旁观者回复：“能遇到喜欢的东西太幸运了，这份快乐值得分享”
- 粉丝评论（路人乙）：“感觉自己被困住了，好想自由一点”
  旁观者回复：“被束缚的感觉确实不好受，先让心情松快下来就好”
- 粉丝评论（路人乙）：“上班好累，好想摆烂”
  旁观者回复：“打工人的疲惫都懂，偶尔松口气不是偷懒，是好好照顾自己”

现在请根据粉丝（路人乙）的评论内容，按照以上要求以旁观者视角回复，每一条都要自然、暖心、像陌生人之间的善意搭话～
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