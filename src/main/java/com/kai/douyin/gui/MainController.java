package com.kai.douyin.gui;

import com.kai.douyin.config.AdbConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * 主界面控制器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainController implements Initializable {

    private final AdbConfig adbConfig;
    private final GuiCoordinateConfig coordinateConfig;
    private final GuiRpaService guiRpaService;

    // ==================== 设备配置 ====================
    @FXML private TextField deviceIdField;
    @FXML private TextField adbPathField;
    @FXML private Label connectionStatus;
    @FXML private Button connectBtn;
    
    // ==================== 坐标配置 ====================
    @FXML private TextField commentIconXField;
    @FXML private TextField commentIconYField;
    @FXML private TextField commentInputXField;
    @FXML private TextField commentInputYField;
    @FXML private TextField sendBtnXField;
    @FXML private TextField sendBtnYField;
    
    // ==================== 互关互评配置 ====================
    @FXML private TextField followBtnXField;
    @FXML private TextField followBtnYField;
    
    // ==================== 任务配置 ====================
    @FXML private TextField videoCountField;
    @FXML private TextField commentTextField;
    @FXML private TextField intervalField;
    
    // ==================== 控制按钮 ====================
    @FXML private Button startAiTaskBtn;
    @FXML private Button startFollowTaskBtn;
    @FXML private Button stopTaskBtn;
    @FXML private Button testOcrBtn;
    @FXML private Button testSwipeBtn;
    @FXML private Button testOpenCommentBtn;
    @FXML private Button testCloseCommentBtn;
    
    // ==================== 日志显示 ====================
    @FXML private TextArea logArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    
    private volatile boolean taskRunning = false;
    private Thread currentTask;
    
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // 初始化默认值
        initDefaultValues();
        
        // 设置按钮事件
        setupButtonActions();
        
        // 设置日志回调
        guiRpaService.setLogCallback(this::appendLog);
        
        // 初始状态
        updateConnectionStatus(false);
        stopTaskBtn.setDisable(true);
        
        appendLog("程序启动成功，请配置设备信息后连接设备");
    }

    /**
     * 初始化默认值
     */
    private void initDefaultValues() {
        // 设备配置
        deviceIdField.setText(adbConfig.getDeviceId() != null ? adbConfig.getDeviceId() : "");
        adbPathField.setText(adbConfig.getPath() != null ? adbConfig.getPath() : "D:/adb/adb.exe");
        
        // 评论区坐标（默认值）
        commentIconXField.setText("1000");
        commentIconYField.setText("1570");
        commentInputXField.setText("170");
        commentInputYField.setText("2006");
        sendBtnXField.setText("966");
        sendBtnYField.setText("2180");
        
        // 关注按钮坐标
        followBtnXField.setText("950");
        followBtnYField.setText("350");
        
        // 任务配置
        videoCountField.setText("10");
        commentTextField.setText("互关互关");
        intervalField.setText("3000");
    }

    /**
     * 设置按钮事件
     */
    private void setupButtonActions() {
        // 连接按钮
        connectBtn.setOnAction(e -> connectDevice());
        
        // 启动AI评论任务
        startAiTaskBtn.setOnAction(e -> startAiCommentTask());
        
        // 启动互关互评任务
        startFollowTaskBtn.setOnAction(e -> startFollowCommentTask());
        
        // 停止任务
        stopTaskBtn.setOnAction(e -> stopCurrentTask());
        
        // 测试按钮
        testOcrBtn.setOnAction(e -> testOcrRecognition());
        testSwipeBtn.setOnAction(e -> testSwipe());
        testOpenCommentBtn.setOnAction(e -> testOpenComment());
        testCloseCommentBtn.setOnAction(e -> testCloseComment());
    }

    /**
     * 连接设备
     */
    @FXML
    private void connectDevice() {
        String deviceId = deviceIdField.getText().trim();
        String adbPath = adbPathField.getText().trim();
        
        if (deviceId.isEmpty()) {
            showAlert("提示", "请输入设备ID");
            return;
        }
        
        appendLog("正在连接设备: " + deviceId);
        
        new Thread(() -> {
            try {
                // 更新配置
                adbConfig.setDeviceId(deviceId);
                adbConfig.setPath(adbPath);
                
                // 检查连接
                boolean connected = guiRpaService.isDeviceConnected();
                
                Platform.runLater(() -> {
                    updateConnectionStatus(connected);
                    if (connected) {
                        int[] screenSize = guiRpaService.getScreenSize();
                        appendLog("设备连接成功! 屏幕分辨率: " + screenSize[0] + "x" + screenSize[1]);
                    } else {
                        appendLog("设备连接失败，请检查设备ID和ADB配置");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    updateConnectionStatus(false);
                    appendLog("连接异常: " + ex.getMessage());
                });
            }
        }).start();
    }

    /**
     * 启动AI评论任务
     */
    private void startAiCommentTask() {
        if (taskRunning) {
            showAlert("提示", "任务正在运行中");
            return;
        }
        
        // 更新坐标配置
        updateCoordinateConfig();
        
        int videoCount = parseIntOrDefault(videoCountField.getText(), 10);
        
        appendLog("启动AI评论任务，目标视频数: " + videoCount);
        setTaskRunning(true);
        
        currentTask = new Thread(() -> {
            try {
                guiRpaService.runAiCommentTask(videoCount);
            } catch (Exception ex) {
                Platform.runLater(() -> appendLog("任务异常: " + ex.getMessage()));
            } finally {
                Platform.runLater(() -> setTaskRunning(false));
            }
        });
        currentTask.start();
    }

    /**
     * 启动互关互评任务
     */
    private void startFollowCommentTask() {
        if (taskRunning) {
            showAlert("提示", "任务正在运行中");
            return;
        }
        
        // 更新坐标配置
        updateCoordinateConfig();
        
        int videoCount = parseIntOrDefault(videoCountField.getText(), 10);
        String commentText = commentTextField.getText().trim();
        if (commentText.isEmpty()) {
            commentText = "互关互关";
        }
        
        final String finalComment = commentText;
        appendLog("启动互关互评任务，目标视频数: " + videoCount + "，评论内容: " + finalComment);
        setTaskRunning(true);
        
        currentTask = new Thread(() -> {
            try {
                guiRpaService.runFollowCommentTask(videoCount, finalComment);
            } catch (Exception ex) {
                Platform.runLater(() -> appendLog("任务异常: " + ex.getMessage()));
            } finally {
                Platform.runLater(() -> setTaskRunning(false));
            }
        });
        currentTask.start();
    }

    /**
     * 停止当前任务
     */
    private void stopCurrentTask() {
        guiRpaService.stopTask();
        if (currentTask != null && currentTask.isAlive()) {
            currentTask.interrupt();
            appendLog("正在停止任务...");
        }
        setTaskRunning(false);
    }

    /**
     * 测试OCR识别
     */
    private void testOcrRecognition() {
        appendLog("测试OCR识别...");
        new Thread(() -> {
            try {
                var comments = guiRpaService.testOcrRecognition();
                Platform.runLater(() -> {
                    appendLog("OCR识别完成，识别到 " + comments.size() + " 条评论");
                    for (var c : comments) {
                        appendLog("  - [" + c.getUserName() + "] " + c.getContent());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> appendLog("OCR测试失败: " + ex.getMessage()));
            }
        }).start();
    }

    /**
     * 测试滑动
     */
    private void testSwipe() {
        appendLog("测试滑动到下一个视频...");
        new Thread(() -> {
            try {
                guiRpaService.swipeToNextVideo();
                Platform.runLater(() -> appendLog("滑动成功"));
            } catch (Exception ex) {
                Platform.runLater(() -> appendLog("滑动失败: " + ex.getMessage()));
            }
        }).start();
    }

    /**
     * 测试打开评论区
     */
    private void testOpenComment() {
        updateCoordinateConfig();
        appendLog("测试打开评论区...");
        new Thread(() -> {
            try {
                guiRpaService.openCommentSection();
                Platform.runLater(() -> appendLog("评论区已打开"));
            } catch (Exception ex) {
                Platform.runLater(() -> appendLog("打开评论区失败: " + ex.getMessage()));
            }
        }).start();
    }

    /**
     * 测试关闭评论区
     */
    private void testCloseComment() {
        appendLog("测试关闭评论区...");
        new Thread(() -> {
            try {
                guiRpaService.closeCommentSection();
                Platform.runLater(() -> appendLog("评论区已关闭"));
            } catch (Exception ex) {
                Platform.runLater(() -> appendLog("关闭评论区失败: " + ex.getMessage()));
            }
        }).start();
    }

    /**
     * 更新连接状态
     */
    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            connectionStatus.setText("已连接");
            connectionStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            startAiTaskBtn.setDisable(false);
            startFollowTaskBtn.setDisable(false);
        } else {
            connectionStatus.setText("未连接");
            connectionStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            startAiTaskBtn.setDisable(true);
            startFollowTaskBtn.setDisable(true);
        }
    }

    /**
     * 设置任务运行状态
     */
    private void setTaskRunning(boolean running) {
        taskRunning = running;
        Platform.runLater(() -> {
            startAiTaskBtn.setDisable(running);
            startFollowTaskBtn.setDisable(running);
            stopTaskBtn.setDisable(!running);
            progressBar.setProgress(running ? -1 : 0);
            statusLabel.setText(running ? "任务运行中..." : "空闲");
        });
    }

    /**
     * 添加日志
     */
    private void appendLog(String message) {
        String time = LocalDateTime.now().format(timeFormatter);
        String logMessage = "[" + time + "] " + message + "\n";
        
        if (Platform.isFxApplicationThread()) {
            logArea.appendText(logMessage);
            logArea.setScrollTop(Double.MAX_VALUE);
        } else {
            Platform.runLater(() -> {
                logArea.appendText(logMessage);
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }
        
        log.info(message);
    }

    /**
     * 显示提示框
     */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 解析整数，失败返回默认值
     */
    private int parseIntOrDefault(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 清空日志
     */
    @FXML
    private void clearLog() {
        logArea.clear();
        appendLog("日志已清空");
    }
    
    /**
     * 保存配置
     */
    @FXML
    private void saveConfig() {
        try {
            // 更新ADB配置
            adbConfig.setDeviceId(deviceIdField.getText().trim());
            adbConfig.setPath(adbPathField.getText().trim());
            
            // 更新坐标配置
            updateCoordinateConfig();
            
            appendLog("配置已保存");
            showAlert("成功", "配置保存成功！");
        } catch (Exception ex) {
            appendLog("保存配置失败: " + ex.getMessage());
        }
    }
    
    /**
     * 从界面更新坐标配置
     */
    private void updateCoordinateConfig() {
        coordinateConfig.setCommentIconX(parseIntOrDefault(commentIconXField.getText(), 1000));
        coordinateConfig.setCommentIconY(parseIntOrDefault(commentIconYField.getText(), 1570));
        coordinateConfig.setCommentInputX(parseIntOrDefault(commentInputXField.getText(), 170));
        coordinateConfig.setCommentInputY(parseIntOrDefault(commentInputYField.getText(), 2006));
        coordinateConfig.setSendBtnX(parseIntOrDefault(sendBtnXField.getText(), 966));
        coordinateConfig.setSendBtnY(parseIntOrDefault(sendBtnYField.getText(), 2180));
        coordinateConfig.setFollowBtnX(parseIntOrDefault(followBtnXField.getText(), 993));
        coordinateConfig.setFollowBtnY(parseIntOrDefault(followBtnYField.getText(), 1066));
        coordinateConfig.setIntervalMs(parseLongOrDefault(intervalField.getText(), 3000));
    }
    
    /**
     * 解析长整数，失败返回默认值
     */
    private long parseLongOrDefault(String text, long defaultValue) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
