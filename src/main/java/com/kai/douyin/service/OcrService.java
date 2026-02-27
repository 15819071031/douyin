package com.kai.douyin.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.kai.douyin.config.OcrConfig;
import com.kai.douyin.dal.dto.CommentInfo;
import com.kai.douyin.utils.AdbCommandUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final OcrConfig ocrConfig;
    private final AdbCommandUtil adbCommand;
    private final OkHttpClient httpClient = new OkHttpClient();

    /**
     * 截图并识别评论区内容，返回评论列表
     */
    public List<CommentInfo> captureAndRecognizeComments() {
        try {
            // 1. 确保截图目录存在
            ensureDirectoryExists(ocrConfig.getScreenshotDir());
            
            // 2. 截取屏幕
            String screenshotPath = captureScreen();
            if (screenshotPath == null) {
                log.error("截图失败，请检查ADB连接");
                return new ArrayList<>();
            }
            log.info("屏幕截图保存到: {}", screenshotPath);
            
            // 3. 裁剪评论区域
            String croppedPath = cropCommentArea(screenshotPath);
            if (croppedPath == null) {
                log.error("裁剪评论区域失败");
                return new ArrayList<>();
            }
            log.info("裁剪评论区域保存到: {}", croppedPath);
            
            // 4. OCR识别
            String recognizedText;
            if ("paddleocr".equalsIgnoreCase(ocrConfig.getEngine())) {
                recognizedText = recognizeWithPaddleOcr(croppedPath);
            } else {
                recognizedText = recognizeWithTesseract(croppedPath);
            }
            log.info("OCR识别结果:\n{}", recognizedText);
            
            // 5. 解析评论
            List<CommentInfo> comments = parseComments(recognizedText);
            log.info("解析到 {} 条评论", comments.size());
            
            return comments;
        } catch (Exception e) {
            log.error("捕获和识别评论失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 确保目录存在
     */
    private void ensureDirectoryExists(String dir) {
        try {
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            log.error("创建目录失败: {}", dir, e);
        }
    }

    /**
     * 截取屏幕
     * @return 截图路径，失败返回null
     */
    private String captureScreen() {
        String fileName = "screenshot_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
        String savePath = ocrConfig.getScreenshotDir() + "/" + fileName;
        return adbCommand.captureScreen(savePath);
    }

    /**
     * 裁剪评论区域
     * @return 裁剪后的图片路径，失败返回null
     */
    private String cropCommentArea(String originalPath) {
        try {
            File originalFile = new File(originalPath);
            if (!originalFile.exists()) {
                log.error("原始截图文件不存在: {}", originalPath);
                return null;
            }
            
            BufferedImage originalImage = ImageIO.read(originalFile);
            if (originalImage == null) {
                log.error("无法读取图片文件: {}", originalPath);
                return null;
            }
            
            int x = ocrConfig.getCommentAreaX();
            int y = ocrConfig.getCommentAreaY();
            int width = Math.min(ocrConfig.getCommentAreaWidth(), originalImage.getWidth() - x);
            int height = Math.min(ocrConfig.getCommentAreaHeight(), originalImage.getHeight() - y);
            
            // 防止越界
            if (x < 0 || y < 0 || width <= 0 || height <= 0) {
                log.error("裁剪区域参数无效: x={}, y={}, width={}, height={}", x, y, width, height);
                return null;
            }
            
            // 裁剪图像
            BufferedImage croppedImage = originalImage.getSubimage(x, y, width, height);
            
            // 图像预处理：转灰度、增强对比度
            croppedImage = preprocessImage(croppedImage);
            
            String croppedPath = originalPath.replace(".png", "_cropped.png");
            ImageIO.write(croppedImage, "png", new File(croppedPath));
            
            return croppedPath;
        } catch (Exception e) {
            log.error("裁剪评论区域失败", e);
            return null;
        }
    }

    /**
     * 图像预处理，提高OCR识别率
     */
    private BufferedImage preprocessImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        BufferedImage grayImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // 灰度转换
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                
                // 二值化处理，提高对比度
                gray = gray > 180 ? 255 : (gray < 80 ? 0 : gray);
                
                int grayRgb = (gray << 16) | (gray << 8) | gray;
                grayImage.setRGB(x, y, grayRgb);
            }
        }
        
        return grayImage;
    }

    /**
     * 使用Tesseract命令行进行OCR识别（更稳定）
     */
    private String recognizeWithTesseract(String imagePath) {
        try {
            // 输出文件路径（不带扩展名，tesseract会自动加.txt）
            String outputBase = imagePath.replace(".png", "_ocr");
            String outputFile = outputBase + ".txt";
            
            // 构建tesseract命令
            // tesseract <input> <output_base> -l <lang> --psm 6
            String command = String.format("\"%s/tesseract.exe\" \"%s\" \"%s\" -l %s --psm 6",
                    ocrConfig.getTesseractPath(), imagePath, outputBase, ocrConfig.getLanguage());
            
            log.info("执行Tesseract命令: {}", command);
            
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取命令输出（用于调试）
            StringBuilder cmdOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    cmdOutput.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Tesseract执行超时");
                return "";
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Tesseract执行失败, exitCode: {}, output: {}", exitCode, cmdOutput);
                return "";
            }
            
            // 读取识别结果
            File resultFile = new File(outputFile);
            if (resultFile.exists()) {
                String result = Files.readString(resultFile.toPath(), StandardCharsets.UTF_8);
                log.info("Tesseract识别完成，结果长度: {}", result.length());
                return result;
            } else {
                log.error("Tesseract输出文件不存在: {}", outputFile);
                return "";
            }
            
        } catch (Exception e) {
            log.error("Tesseract识别失败", e);
            return "";
        }
    }

    /**
     * 使用PaddleOCR服务进行识别
     */
    private String recognizeWithPaddleOcr(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", imageFile.getName(),
                            RequestBody.create(imageBytes, MediaType.parse("image/png")))
                    .build();
            
            Request request = new Request.Builder()
                    .url(ocrConfig.getPaddleOcrUrl())
                    .post(requestBody)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return parsePaddleOcrResponse(responseBody);
                }
            }
        } catch (IOException e) {
            log.error("PaddleOCR请求失败", e);
        }
        return "";
    }

    /**
     * 解析PaddleOCR返回结果
     */
    private String parsePaddleOcrResponse(String response) {
        StringBuilder result = new StringBuilder();
        try {
            JSONObject json = JSON.parseObject(response);
            JSONArray results = json.getJSONArray("results");
            
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String text = item.getString("text");
                    if (text != null && !text.isEmpty()) {
                        result.append(text).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析PaddleOCR响应失败", e);
        }
        return result.toString();
    }

    /**
     * 解析OCR识别结果，提取评论信息
     * 抖音评论格式：用户名 -> 评论内容(可能多行) -> 时间·地点 回复 -> 点赞数
     */
    private List<CommentInfo> parseComments(String ocrText) {
        List<CommentInfo> comments = new ArrayList<>();
                
        if (ocrText == null || ocrText.isEmpty()) {
            return comments;
        }
                
        // 预处理：去除多余空格（OCR经常把字符分开）
        ocrText = preprocessOcrText(ocrText);
        log.debug("预处理后OCR文本:\n{}", ocrText);
                
        String[] lines = ocrText.split("\n");
                
        // 正则模式
        // 时间+地点行：如 "昨天20:44 · 北京 回复" 或 "1分钟前 · 广东 回复"
        Pattern timeLocationPattern = Pattern.compile(
            "(\\d+分钟前|\\d+小时前|\\d+天前|昨天|前天|\\d+月\\d+日|\\d{1,2}[:：]\\d{2}).*([·•\\.]|危\u5730|北京|上海|广东|浙江|江苏|四川|湖南|湖北|山东|河南|河北)"
        );
        // 点赞数模式：如 "3.0万" "458"
        Pattern likePattern = Pattern.compile("^\\d+\\.?\\d*[万wWkK]?$");
        // 展开回复模式
        Pattern expandPattern = Pattern.compile("展开\\d+条回复");
        // 系统文本模式
        Pattern systemPattern = Pattern.compile(
            "^(发条评论|和大家一起讨论|查看更多|全部评论|热门评论|最新评论|评论|回复|作者|置顶)"
        );
                
        // 评论解析状态
        String currentUser = null;
        StringBuilder contentBuilder = new StringBuilder();
            
        // 先找出所有时间地点行的索引（这些行标志着一条评论的结束）
        List<Integer> timeLineIndices = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (timeLocationPattern.matcher(line).find()) {
                timeLineIndices.add(i);
            }
        }
            
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.length() < 2) {
                continue;
            }
                    
            // 跳过系统文本
            if (systemPattern.matcher(line).find()) {
                continue;
            }
                    
            // 跳过展开回复
            if (expandPattern.matcher(line).find()) {
                continue;
            }
                    
            // 跳过点赞数
            if (likePattern.matcher(line).matches()) {
                continue;
            }
                
            // 检查是否是时间+地点行（表示当前评论结束）
            if (timeLocationPattern.matcher(line).find()) {
                // 保存当前评论
                if (contentBuilder.length() > 0) {
                    saveCleanComment(comments, currentUser, contentBuilder.toString().trim());
                }
                currentUser = null;
                contentBuilder = new StringBuilder();
                continue;
            }
                
            // 判断这行是用户名还是评论内容
            // 用户名特征：通常较短（<15字），且后面在遇到时间行之前都是评论内容
            boolean isUserName = false;
                
            if (currentUser == null && line.length() <= 15) {
                // 查看后面最近的时间行，中间的内容都应该是评论
                int nextTimeLine = -1;
                for (int idx : timeLineIndices) {
                    if (idx > i) {
                        nextTimeLine = idx;
                        break;
                    }
                }
                    
                // 如果后面有时间行，且中间至少有一行内容，则当前行是用户名
                if (nextTimeLine > i + 1) {
                    isUserName = true;
                }
            }
                    
            if (isUserName) {
                // 保存之前的评论
                if (contentBuilder.length() > 0) {
                    saveCleanComment(comments, currentUser, contentBuilder.toString().trim());
                }
                // 开始新评论
                currentUser = cleanUserName(line);
                contentBuilder = new StringBuilder();
            } else {
                // 这是评论内容，累加到当前评论
                String cleanLine = cleanCommentContent(line);
                if (cleanLine.length() >= 2) {
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("");
                    }
                    contentBuilder.append(cleanLine);
                }
            }
        }
                
        // 处理最后一条评论
        if (contentBuilder.length() > 0) {
            saveCleanComment(comments, currentUser, contentBuilder.toString().trim());
        }
                
        return comments;
    }
        
    /**
     * 预处理OCR文本：去除多余空格
     */
    private String preprocessOcrText(String text) {
        // 去除每个字符之间的空格（中文字符间）
        // 保留英文单词间的空格
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n");
            
        for (String line : lines) {
            // 去除行内的多余空格
            String cleanLine = line.replaceAll("\\s+", " ").trim();
            // 去除中文字符间的空格
            cleanLine = removeSpacesBetweenChinese(cleanLine);
            if (!cleanLine.isEmpty()) {
                result.append(cleanLine).append("\n");
            }
        }
            
        return result.toString();
    }
        
    /**
     * 去除中文字符间的空格
     */
    private String removeSpacesBetweenChinese(String text) {
        StringBuilder result = new StringBuilder();
        char[] chars = text.toCharArray();
            
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
                
            // 如果是空格，检查前后是否都是中文字符
            if (c == ' ' && i > 0 && i < chars.length - 1) {
                char prev = chars[i - 1];
                char next = chars[i + 1];
                // 如果前后都是中文或特殊字符，跳过空格
                if (isChinese(prev) && isChinese(next)) {
                    continue;
                }
            }
            result.append(c);
        }
            
        return result.toString();
    }
        
    /**
     * 判断是否是中文字符
     */
    private boolean isChinese(char c) {
        return (c >= 0x4e00 && c <= 0x9fff)  // CJK统一汉字
            || (c >= 0x3000 && c <= 0x303f)  // 中文标点
            || (c >= 0xff00 && c <= 0xffef); // 全角字符
    }
        
    /**
     * 清理用户名
     */
    private String cleanUserName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        // 去除@符号和多余字符
        name = name.replaceAll("^[@\uff20\\s]+", "").trim();
        name = name.replaceAll("[:\uff1a\u00b7•]+$", "").trim();
        // 去除表情乱码
        name = removeEmoji(name);
        return name.isEmpty() ? null : name;
    }
        
    /**
     * 清理评论内容
     */
    private String cleanCommentContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
            
        // 去除@某人:的前缀（回复他人）
        content = content.replaceAll("^[@\uff20][^::：]*[::：]\\s*", "");
            
        // 去除表情乱码
        content = removeEmoji(content);
            
        // 去除特殊字符
        content = content.replaceAll("[\uf000-\uffff]", ""); // Unicode私用区
        content = content.replaceAll("[Q\u2661\u2665\u2764]+$", ""); // 末尾的点赞符号
            
        return content.trim();
    }
        
    /**
     * 去除emoji和特殊字符
     */
    private String removeEmoji(String text) {
        if (text == null) return null;
            
        // 去除常见的emoji范围
        return text.replaceAll("[\ud83c-\ud83f][\udc00-\udfff]", "") // 表情符号
                   .replaceAll("[\u2600-\u27bf]", "")              // 杂项符号
                   .replaceAll("[\u231a-\u23f3]", "")              // 技术符号
                   .replaceAll("园", "")                           // 表情乱码
                   .replaceAll("[\u0080-\u009f]", "")              // 控制字符
                   .replaceAll("\\s{2,}", " ")                     // 多个空格合并
                   .trim();
    }
        
    /**
     * 保存清理后的评论
     */
    private void saveCleanComment(List<CommentInfo> comments, String userName, String content) {
        if (content == null || content.length() < 2) {
            return;
        }
            
        // 过滤明显不是评论的内容
        if (content.matches(".*\u5c55\u5f00.*\u6761\u56de\u590d.*") 
            || content.matches(".*\u56de\u590d.*")
            || content.length() < 3) {
            return;
        }
            
        CommentInfo comment = new CommentInfo();
        comment.setUserName(userName != null ? userName : "未知用户");
        comment.setContent(content);
        comment.setSource("ocr");
            
        log.info("解析评论 - 用户: {}, 内容: {}", userName, content);
        comments.add(comment);
    }

    /**
     * 判断是否是系统文本（需要过滤）
     */
    private boolean isSystemText(String text) {
        String[] systemTexts = {
            "回复", "展开", "收起", "查看更多", "全部评论", 
            "相关评论", "热门评论", "最新评论", "条回复",
            "点赞", "分享", "收藏", "转发", "复制链接"
        };
        
        for (String sys : systemTexts) {
            if (text.contains(sys)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 直接识别指定区域的文字
     */
    public String recognizeArea(int x, int y, int width, int height) {
        try {
            ensureDirectoryExists(ocrConfig.getScreenshotDir());
            String screenshotPath = captureScreen();
            
            File originalFile = new File(screenshotPath);
            BufferedImage originalImage = ImageIO.read(originalFile);
            
            // 裁剪指定区域
            width = Math.min(width, originalImage.getWidth() - x);
            height = Math.min(height, originalImage.getHeight() - y);
            
            BufferedImage croppedImage = originalImage.getSubimage(x, y, width, height);
            croppedImage = preprocessImage(croppedImage);
            
            String croppedPath = screenshotPath.replace(".png", "_area.png");
            ImageIO.write(croppedImage, "png", new File(croppedPath));
            
            if ("paddleocr".equalsIgnoreCase(ocrConfig.getEngine())) {
                return recognizeWithPaddleOcr(croppedPath);
            } else {
                return recognizeWithTesseract(croppedPath);
            }
        } catch (Exception e) {
            log.error("区域识别失败", e);
            return "";
        }
    }
}
