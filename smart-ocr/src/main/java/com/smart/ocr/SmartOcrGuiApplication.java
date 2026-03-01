package com.smart.ocr;

import com.smart.ocr.service.HotkeyService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * SmartOCR JavaFX图形化界面启动类
 * 整合Spring Boot与JavaFX，支持系统托盘
 */
public class SmartOcrGuiApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private TrayIcon trayIcon;
    private Stage primaryStage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() throws Exception {
        // 启动Spring Boot上下文，禁用Web服务器
        springContext = new SpringApplicationBuilder(SmartOcrApplication.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        
        // 加载FXML布局
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(springContext::getBean);
        
        Parent root = loader.load();
        
        // 设置场景
        Scene scene = new Scene(root, 900, 650);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/style.css")).toExternalForm());
        
        // 配置主窗口
        stage.setTitle("SmartOCR - AI智能识别工具 v1.0");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        
        // 加载图标
        try {
            InputStream iconStream = getClass().getResourceAsStream("/images/icon.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {}
        
        // 设置系统托盘
        setupSystemTray();
        
        // 窗口关闭时最小化到托盘
        stage.setOnCloseRequest(event -> {
            event.consume();
            minimizeToTray();
        });
        
        // 初始化全局热键
        initHotkeys();
        
        stage.show();
    }

    /**
     * 设置系统托盘
     */
    private void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        
        try {
            SystemTray tray = SystemTray.getSystemTray();
            
            // 创建托盘图标
            BufferedImage image;
            InputStream iconStream = getClass().getResourceAsStream("/images/tray.png");
            if (iconStream != null) {
                image = ImageIO.read(iconStream);
            } else {
                // 创建默认图标
                image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = image.createGraphics();
                g2d.setColor(new Color(64, 158, 255));
                g2d.fillRoundRect(0, 0, 16, 16, 4, 4);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Arial", Font.BOLD, 10));
                g2d.drawString("S", 4, 12);
                g2d.dispose();
            }
            
            trayIcon = new TrayIcon(image, "SmartOCR");
            trayIcon.setImageAutoSize(true);
            
            // 创建托盘菜单
            PopupMenu popup = new PopupMenu();
            
            MenuItem showItem = new MenuItem("显示主窗口");
            showItem.addActionListener(e -> Platform.runLater(this::showMainWindow));
            
            MenuItem screenshotItem = new MenuItem("截图识别 (F4)");
            screenshotItem.addActionListener(e -> Platform.runLater(this::triggerScreenshot));
            
            MenuItem exitItem = new MenuItem("退出");
            exitItem.addActionListener(e -> exitApplication());
            
            popup.add(showItem);
            popup.addSeparator();
            popup.add(screenshotItem);
            popup.addSeparator();
            popup.add(exitItem);
            
            trayIcon.setPopupMenu(popup);
            
            // 双击托盘图标显示窗口
            trayIcon.addActionListener(e -> Platform.runLater(this::showMainWindow));
            
            tray.add(trayIcon);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化全局热键
     */
    private void initHotkeys() {
        try {
            HotkeyService hotkeyService = springContext.getBean(HotkeyService.class);
            hotkeyService.registerHotkeys();
        } catch (Exception e) {
            System.err.println("热键注册失败: " + e.getMessage());
        }
    }

    /**
     * 最小化到托盘
     */
    private void minimizeToTray() {
        if (trayIcon != null) {
            primaryStage.hide();
            trayIcon.displayMessage("SmartOCR", "程序已最小化到系统托盘，按F4快捷截图识别", TrayIcon.MessageType.INFO);
        } else {
            // 不支持托盘则直接最小化
            primaryStage.setIconified(true);
        }
    }

    /**
     * 显示主窗口
     */
    private void showMainWindow() {
        primaryStage.show();
        primaryStage.setIconified(false);
        primaryStage.toFront();
    }

    /**
     * 触发截图
     */
    private void triggerScreenshot() {
        try {
            HotkeyService hotkeyService = springContext.getBean(HotkeyService.class);
            hotkeyService.triggerScreenshot();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 退出应用
     */
    private void exitApplication() {
        // 注销热键
        try {
            HotkeyService hotkeyService = springContext.getBean(HotkeyService.class);
            hotkeyService.unregisterHotkeys();
        } catch (Exception ignored) {}
        
        // 移除托盘图标
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        
        // 关闭Spring上下文
        if (springContext != null) {
            springContext.close();
        }
        
        Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() throws Exception {
        exitApplication();
    }
}
