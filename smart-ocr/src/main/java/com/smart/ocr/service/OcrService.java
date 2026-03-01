package com.smart.ocr.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.smart.ocr.config.OcrConfig;
import com.smart.ocr.dto.OcrResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OCR识别服务
 * 支持Tesseract和PaddleOCR两种引擎
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final OcrConfig ocrConfig;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private ITesseract tesseract;

    @PostConstruct
    public void init() {
        // 确保临时目录存在
        ensureDirectoryExists(ocrConfig.getTempDir());
        
        // 初始化Tesseract
        try {
            tesseract = new Tesseract();
            tesseract.setDatapath(ocrConfig.getTessdataPath());
            tesseract.setLanguage(ocrConfig.getLanguage());
            tesseract.setPageSegMode(6); // 自动分块
            tesseract.setOcrEngineMode(1); // LSTM引擎
            log.info("Tesseract初始化成功，数据路径: {}", ocrConfig.getTessdataPath());
        } catch (Exception e) {
            log.warn("Tesseract初始化失败，将使用命令行方式: {}", e.getMessage());
            tesseract = null;
        }
    }

    /**
     * 识别图片中的文字
     *
     * @param imagePath 图片路径
     * @return OCR识别结果
     */
    public OcrResult recognize(String imagePath) {
        OcrResult result = new OcrResult();
        result.setSourcePath(imagePath);
        result.setStartTime(System.currentTimeMillis());

        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                result.setSuccess(false);
                result.setErrorMessage("图片文件不存在: " + imagePath);
                return result;
            }

            // 读取并预处理图片
            BufferedImage originalImage = ImageIO.read(imageFile);
            BufferedImage processedImage = preprocessImage(originalImage);
            
            // 保存预处理后的图片
            String processedPath = saveProcessedImage(processedImage);
            
            // 执行OCR识别
            String text;
            if ("paddleocr".equalsIgnoreCase(ocrConfig.getEngine())) {
                text = recognizeWithPaddleOcr(processedPath);
            } else {
                text = recognizeWithTesseract(processedPath);
            }

            result.setText(text);
            result.setSuccess(true);
            result.setCharCount(text.length());
            result.setLineCount(text.split("\n").length);
            
        } catch (Exception e) {
            log.error("OCR识别失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setEndTime(System.currentTimeMillis());
        result.setProcessTime(result.getEndTime() - result.getStartTime());
        return result;
    }

    /**
     * 从BufferedImage识别文字
     */
    public OcrResult recognize(BufferedImage image) {
        try {
            // 保存临时图片
            String tempPath = ocrConfig.getTempDir() + "/temp_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            ImageIO.write(image, "png", new File(tempPath));
            return recognize(tempPath);
        } catch (Exception e) {
            OcrResult result = new OcrResult();
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 识别剪贴板图片
     */
    public OcrResult recognizeFromClipboard(BufferedImage clipboardImage) {
        return recognize(clipboardImage);
    }

    /**
     * 图像预处理
     */
    private BufferedImage preprocessImage(BufferedImage image) {
        if (image == null) return null;

        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // 灰度转换
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                if (ocrConfig.isEnableBinarize()) {
                    // 二值化
                    gray = gray > ocrConfig.getBinarizeThreshold() ? 255 : 0;
                }

                int newRgb = (gray << 16) | (gray << 8) | gray;
                result.setRGB(x, y, newRgb);
            }
        }

        return result;
    }

    /**
     * 保存预处理后的图片
     */
    private String saveProcessedImage(BufferedImage image) throws Exception {
        String path = ocrConfig.getTempDir() + "/processed_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
        ImageIO.write(image, "png", new File(path));
        return path;
    }

    /**
     * 使用Tesseract识别
     */
    private String recognizeWithTesseract(String imagePath) {
        // 优先使用Tess4J
        if (tesseract != null) {
            try {
                File imageFile = new File(imagePath);
                String result = tesseract.doOCR(imageFile);
                log.info("Tess4J识别完成，文字长度: {}", result.length());
                return cleanOcrResult(result);
            } catch (Exception e) {
                log.warn("Tess4J识别失败，尝试命令行方式: {}", e.getMessage());
            }
        }

        // 回退到命令行方式
        return recognizeWithTesseractCmd(imagePath);
    }

    /**
     * 使用Tesseract命令行识别
     */
    private String recognizeWithTesseractCmd(String imagePath) {
        try {
            String outputBase = imagePath.replace(".png", "_ocr");
            String outputFile = outputBase + ".txt";

            String command = String.format("\"%s/tesseract.exe\" \"%s\" \"%s\" -l %s --psm 6",
                    ocrConfig.getTesseractPath(), imagePath, outputBase, ocrConfig.getLanguage());

            log.info("执行Tesseract命令: {}", command);

            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

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
                throw new RuntimeException("Tesseract执行超时");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("Tesseract执行失败: " + cmdOutput);
            }

            File resultFile = new File(outputFile);
            if (resultFile.exists()) {
                String result = Files.readString(resultFile.toPath(), StandardCharsets.UTF_8);
                return cleanOcrResult(result);
            }

            return "";
        } catch (Exception e) {
            log.error("Tesseract命令行识别失败", e);
            return "";
        }
    }

    /**
     * 使用PaddleOCR识别
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
        } catch (Exception e) {
            log.error("PaddleOCR请求失败", e);
        }
        return "";
    }

    /**
     * 解析PaddleOCR响应
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
        return cleanOcrResult(result.toString());
    }

    /**
     * 清理OCR结果
     */
    private String cleanOcrResult(String text) {
        if (text == null) return "";
        
        // 去除多余空行
        text = text.replaceAll("\n{3,}", "\n\n");
        // 去除行首尾空格
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString().trim();
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
        } catch (Exception e) {
            log.error("创建目录失败: {}", dir, e);
        }
    }

    /**
     * 清理临时文件
     */
    public void cleanTempFiles() {
        try {
            File tempDir = new File(ocrConfig.getTempDir());
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            file.delete();
                        }
                    }
                }
            }
            log.info("临时文件清理完成");
        } catch (Exception e) {
            log.error("清理临时文件失败", e);
        }
    }
}
