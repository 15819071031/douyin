package com.smart.ocr.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.image.Image;
import javafx.embed.swing.SwingFXUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;

/**
 * 剪贴板工具类
 */
@Slf4j
public class ClipboardUtil {

    /**
     * 复制文本到剪贴板
     */
    public static void copyText(String text) {
        if (text == null) return;

        try {
            // 使用AWT剪贴板（更稳定）
            StringSelection selection = new StringSelection(text);
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            log.debug("已复制文本到剪贴板，长度: {}", text.length());
        } catch (Exception e) {
            log.error("复制到剪贴板失败", e);
        }
    }

    /**
     * 复制文本到剪贴板（JavaFX版本）
     */
    public static void copyTextFx(String text) {
        if (text == null) return;

        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
            log.debug("已复制文本到剪贴板，长度: {}", text.length());
        } catch (Exception e) {
            log.error("复制到剪贴板失败", e);
        }
    }

    /**
     * 从剪贴板获取文本
     */
    public static String getText() {
        try {
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = clipboard.getContents(null);
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) transferable.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (Exception e) {
            log.error("从剪贴板获取文本失败", e);
        }
        return null;
    }

    /**
     * 复制图片到剪贴板
     */
    public static void copyImage(BufferedImage image) {
        if (image == null) return;

        try {
            ImageTransferable transferable = new ImageTransferable(image);
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(transferable, null);
            log.debug("已复制图片到剪贴板");
        } catch (Exception e) {
            log.error("复制图片到剪贴板失败", e);
        }
    }

    /**
     * 从剪贴板获取图片
     */
    public static BufferedImage getImage() {
        try {
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = clipboard.getContents(null);
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return (BufferedImage) transferable.getTransferData(DataFlavor.imageFlavor);
            }
        } catch (Exception e) {
            log.error("从剪贴板获取图片失败", e);
        }
        return null;
    }

    /**
     * 检查剪贴板是否有图片
     */
    public static boolean hasImage() {
        try {
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查剪贴板是否有文本
     */
    public static boolean hasText() {
        try {
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 图片传输类
     */
    private static class ImageTransferable implements Transferable {
        private final BufferedImage image;

        public ImageTransferable(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (DataFlavor.imageFlavor.equals(flavor)) {
                return image;
            }
            return null;
        }
    }
}
