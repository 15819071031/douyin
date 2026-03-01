package com.smart.ocr.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.UUID;

/**
 * 截图工具类
 */
@Slf4j
public class ScreenshotUtil {

    private static Robot robot;

    static {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            log.error("初始化Robot失败", e);
        }
    }

    /**
     * 截取全屏
     */
    public static BufferedImage captureFullScreen() {
        if (robot == null) return null;

        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        return robot.createScreenCapture(screenRect);
    }

    /**
     * 截取指定区域
     */
    public static BufferedImage captureRegion(int x, int y, int width, int height) {
        if (robot == null) return null;

        Rectangle region = new Rectangle(x, y, width, height);
        return robot.createScreenCapture(region);
    }

    /**
     * 截取指定区域
     */
    public static BufferedImage captureRegion(Rectangle rect) {
        if (robot == null) return null;
        return robot.createScreenCapture(rect);
    }

    /**
     * 保存截图到文件
     */
    public static String saveScreenshot(BufferedImage image, String directory) {
        try {
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = "screenshot_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            File file = new File(dir, fileName);
            ImageIO.write(image, "png", file);
            return file.getAbsolutePath();
        } catch (Exception e) {
            log.error("保存截图失败", e);
            return null;
        }
    }

    /**
     * BufferedImage转JavaFX Image
     */
    public static WritableImage toFxImage(BufferedImage bufferedImage) {
        if (bufferedImage == null) return null;
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    /**
     * JavaFX Image转BufferedImage
     */
    public static BufferedImage fromFxImage(javafx.scene.image.Image fxImage) {
        if (fxImage == null) return null;
        return SwingFXUtils.fromFXImage(fxImage, null);
    }

    /**
     * 获取主屏幕尺寸
     */
    public static Dimension getScreenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    /**
     * 获取所有屏幕的总边界
     */
    public static Rectangle getFullScreenBounds() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle bounds = new Rectangle();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            bounds = bounds.union(gd.getDefaultConfiguration().getBounds());
        }
        return bounds;
    }
}
