package com.kai.douyin;

import com.kai.douyin.gui.DouyinGuiApplication;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;

/**
 * 启动引导类
 * 根据参数决定启动GUI界面还是Web服务
 * 
 * 使用方式:
 * - 默认启动GUI界面
 * - 添加参数 --web 启动Web服务
 */
public class DouyinLauncher {
    
    public static void main(String[] args) {
        boolean webMode = false;
        
        // 检查是否有 --web 参数
        for (String arg : args) {
            if ("--web".equalsIgnoreCase(arg)) {
                webMode = true;
                break;
            }
        }
        
        if (webMode) {
            // Web模式：启动Spring Boot Web服务
            System.out.println("启动Web服务模式...");
            SpringApplication.run(DouyinCommentApplication.class, args);
        } else {
            // GUI模式：启动JavaFX图形界面
            System.out.println("启动GUI界面模式...");
            Application.launch(DouyinGuiApplication.class, args);
        }
    }
}
