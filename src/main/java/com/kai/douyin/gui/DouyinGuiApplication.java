package com.kai.douyin.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 抖音自动评论 - JavaFX图形化界面启动类
 * 整合Spring Boot与JavaFX
 */
public class DouyinGuiApplication extends Application {

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() throws Exception {
        // 启动Spring Boot上下文，禁用Web服务器
        springContext = new SpringApplicationBuilder(com.kai.douyin.DouyinCommentApplication.class)
                .web(WebApplicationType.NONE)  // 不启动Web服务器
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 加载FXML布局
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(springContext::getBean);
        
        Parent root = loader.load();
        
        // 设置窗口属性
        Scene scene = new Scene(root, 900, 700);
        
        // 加载样式表
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        
        primaryStage.setTitle("抖音自动评论助手 v1.0");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        
        // 窗口关闭时退出应用
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
        });
        
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // 关闭Spring上下文
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }
}
