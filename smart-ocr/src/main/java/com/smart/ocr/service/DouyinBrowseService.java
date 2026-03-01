package com.smart.ocr.service;

import com.smart.ocr.config.AdbConfig;
import com.smart.ocr.config.OcrConfig;
import com.smart.ocr.dto.OcrResult;
import com.smart.ocr.dto.VideoScanResult;
import com.smart.ocr.util.AdbUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 抖音自动刷视频服务
 * 功能：刷视频、OCR识别视频文字、关键词匹配点赞
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinBrowseService {

    private final AdbUtil adbUtil;
    private final AdbConfig adbConfig;
    private final OcrConfig ocrConfig;
    private final OcrService ocrService;

    // 停止标志
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);

    // 默认关键词
    private static final List<String> DEFAULT_KEYWORDS = Arrays.asList("幸福", "女性", "独立", "情感");

    /**
     * 开始刷抖音视频
     *
     * @param videoCount      要刷的视频数量
     * @param keywords        关键词列表（包含任一关键词即点赞）
     * @param progressCallback 进度回调
     * @return 扫描结果列表
     */
    public List<VideoScanResult> startBrowsing(int videoCount, List<String> keywords, 
                                                Consumer<VideoScanResult> progressCallback) {
        stopFlag.set(false);
        List<VideoScanResult> results = new ArrayList<>();

        // 检查设备连接
        if (!adbUtil.isDeviceConnected()) {
            log.error("设备未连接，无法开始刷视频");
            VideoScanResult errorResult = new VideoScanResult();
            errorResult.setSuccess(false);
            errorResult.setMessage("设备未连接，请检查ADB连接");
            results.add(errorResult);
            if (progressCallback != null) {
                progressCallback.accept(errorResult);
            }
            return results;
        }

        // 使用默认关键词或传入的关键词
        List<String> targetKeywords = (keywords == null || keywords.isEmpty()) ? DEFAULT_KEYWORDS : keywords;
        log.info("开始刷抖音视频，目标数量: {}, 关键词: {}", videoCount, targetKeywords);

        int likedCount = 0;
        int scannedCount = 0;

        for (int i = 0; i < videoCount && !stopFlag.get(); i++) {
            scannedCount++;
            VideoScanResult result = new VideoScanResult();
            result.setVideoIndex(i + 1);
            result.setTotalCount(videoCount);

            try {
                // 等待视频加载
                Thread.sleep(1500);

                // 1. 截取当前视频画面
                String screenshotPath = captureVideoScreen(i);
                if (screenshotPath == null) {
                    result.setSuccess(false);
                    result.setMessage("截图失败");
                    results.add(result);
                    if (progressCallback != null) {
                        progressCallback.accept(result);
                    }
                    // 继续下一个视频
                    swipeToNextVideo();
                    continue;
                }

                // 2. 裁剪视频文字区域并OCR识别
                String recognizedText = recognizeVideoText(screenshotPath);
                result.setRecognizedText(recognizedText);

                // 3. 关键词匹配
                List<String> matchedKeywords = matchKeywords(recognizedText, targetKeywords);
                result.setMatchedKeywords(matchedKeywords);

                // 4. 如果匹配到关键词，点赞
                if (!matchedKeywords.isEmpty()) {
                    adbUtil.doubleTapLike();
                    result.setLiked(true);
                    likedCount++;
                    log.info("视频 #{} 匹配关键词 {}，已点赞！", i + 1, matchedKeywords);
                    Thread.sleep(500);
                } else {
                    result.setLiked(false);
                    log.info("视频 #{} 未匹配关键词，跳过", i + 1);
                }

                result.setSuccess(true);
                result.setMessage(result.isLiked() ? 
                        "匹配关键词: " + String.join(", ", matchedKeywords) : "未匹配到关键词");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("刷视频任务被中断");
                break;
            } catch (Exception e) {
                log.error("处理视频 #{} 时出错", i + 1, e);
                result.setSuccess(false);
                result.setMessage("处理出错: " + e.getMessage());
            }

            results.add(result);
            if (progressCallback != null) {
                progressCallback.accept(result);
            }

            // 滑动到下一个视频（最后一个不滑动）
            if (i < videoCount - 1 && !stopFlag.get()) {
                swipeToNextVideo();
            }
        }

        log.info("刷视频完成！共扫描 {} 个视频，点赞 {} 个", scannedCount, likedCount);
        return results;
    }

    /**
     * 使用默认关键词刷视频
     */
    public List<VideoScanResult> startBrowsing(int videoCount, Consumer<VideoScanResult> progressCallback) {
        return startBrowsing(videoCount, DEFAULT_KEYWORDS, progressCallback);
    }

    /**
     * 停止刷视频
     */
    public void stopBrowsing() {
        stopFlag.set(true);
        log.info("收到停止信号，即将停止刷视频");
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return !stopFlag.get();
    }

    /**
     * 截取视频画面
     */
    private String captureVideoScreen(int index) {
        String fileName = String.format("video_%d_%s.png", index, UUID.randomUUID().toString().substring(0, 6));
        String savePath = ocrConfig.getTempDir() + "/" + fileName;
        return adbUtil.captureScreen(savePath);
    }

    /**
     * 识别视频中的文字
     */
    private String recognizeVideoText(String screenshotPath) {
        try {
            // 读取截图
            BufferedImage fullImage = ImageIO.read(new File(screenshotPath));
            if (fullImage == null) {
                log.error("无法读取截图: {}", screenshotPath);
                return "";
            }

            // 裁剪视频文字区域（通常在视频下半部分，包含标题、字幕等）
            int x = adbConfig.getVideoTextAreaX();
            int y = adbConfig.getVideoTextAreaY();
            int width = Math.min(adbConfig.getVideoTextAreaWidth(), fullImage.getWidth() - x);
            int height = Math.min(adbConfig.getVideoTextAreaHeight(), fullImage.getHeight() - y);

            // 防止越界
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (width <= 0 || height <= 0) {
                log.warn("裁剪区域无效，使用全图识别");
                OcrResult result = ocrService.recognize(fullImage);
                return result.isSuccess() ? result.getText() : "";
            }

            BufferedImage croppedImage = fullImage.getSubimage(x, y, width, height);

            // OCR识别
            OcrResult result = ocrService.recognize(croppedImage);
            if (result.isSuccess()) {
                log.debug("识别到文字: {}", result.getText());
                return result.getText();
            } else {
                log.warn("OCR识别失败: {}", result.getErrorMessage());
                return "";
            }
        } catch (Exception e) {
            log.error("识别视频文字失败", e);
            return "";
        }
    }

    /**
     * 关键词匹配
     */
    private List<String> matchKeywords(String text, List<String> keywords) {
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
     * 滑动到下一个视频
     */
    private void swipeToNextVideo() {
        try {
            adbUtil.swipeUp();
            Thread.sleep(800); // 等待滑动动画完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取默认关键词列表
     */
    public List<String> getDefaultKeywords() {
        return new ArrayList<>(DEFAULT_KEYWORDS);
    }
}
