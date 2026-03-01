package com.smart.ocr.gui;

import com.smart.ocr.config.AppConfig;
import com.smart.ocr.dto.OcrResult;
import com.smart.ocr.dto.VideoScanResult;
import com.smart.ocr.entity.OcrHistory;
import com.smart.ocr.service.*;
import com.smart.ocr.util.AdbUtil;
import com.smart.ocr.util.ClipboardUtil;
import com.smart.ocr.util.ScreenshotUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * 主界面控制器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainController implements Initializable {

    private final OcrService ocrService;
    private final AiService aiService;
    private final HistoryService historyService;
    private final HotkeyService hotkeyService;
    private final AppConfig appConfig;
    private final DouyinBrowseService douyinBrowseService;
    private final AdbUtil adbUtil;

    // FXML注入的控件 - OCR功能
    @FXML private TextArea resultTextArea;
    @FXML private ImageView previewImageView;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Button screenshotBtn;
    @FXML private Button fileBtn;
    @FXML private Button clipboardBtn;
    @FXML private Button copyBtn;
    @FXML private Button optimizeBtn;
    @FXML private Button translateBtn;
    @FXML private ComboBox<String> translateLangCombo;
    @FXML private ListView<String> historyListView;
    @FXML private TextField searchField;
    @FXML private Label charCountLabel;
    @FXML private CheckBox autoCopyCheckBox;
    @FXML private TabPane mainTabPane;

    // FXML注入的控件 - 抖音刷视频功能
    @FXML private Spinner<Integer> videoCountSpinner;
    @FXML private TextField keywordsField;
    @FXML private Button startBrowseBtn;
    @FXML private Button stopBrowseBtn;
    @FXML private Label deviceStatusLabel;
    @FXML private Label browseProgressLabel;
    @FXML private Label likeCountLabel;
    @FXML private ProgressBar browseProgressBar;
    @FXML private TableView<VideoScanResult> browseResultTable;
    @FXML private TableColumn<VideoScanResult, String> colIndex;
    @FXML private TableColumn<VideoScanResult, String> colText;
    @FXML private TableColumn<VideoScanResult, String> colKeywords;
    @FXML private TableColumn<VideoScanResult, String> colLiked;
    @FXML private TableColumn<VideoScanResult, String> colStatus;

    private BufferedImage currentImage;
    private ObservableList<OcrHistory> historyData = FXCollections.observableArrayList();
    private ObservableList<VideoScanResult> browseResultData = FXCollections.observableArrayList();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private int totalLikeCount = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化翻译语言选项
        translateLangCombo.setItems(FXCollections.observableArrayList(
                "中文", "英文", "日文", "韩文", "法文", "德文", "西班牙文"
        ));
        translateLangCombo.setValue("中文");

        // 初始化自动复制选项
        autoCopyCheckBox.setSelected(appConfig.isAutoCopyResult());

        // 设置热键回调
        hotkeyService.setScreenshotCallback(this::startScreenshot);

        // 加载历史记录
        loadHistory();

        // 监听文本变化更新字数
        resultTextArea.textProperty().addListener((obs, oldVal, newVal) -> {
            int count = newVal != null ? newVal.length() : 0;
            charCountLabel.setText(count + " 字符");
        });

        // 检查AI服务状态
        if (!aiService.isAvailable()) {
            optimizeBtn.setDisable(true);
            translateBtn.setDisable(true);
            statusLabel.setText("提示：AI功能未配置API Key");
        }

        // 初始化抖音刷视频功能
        initDouyinBrowse();

        log.info("SmartOCR主界面初始化完成");
    }

    /**
     * 初始化抖音刷视频功能
     */
    private void initDouyinBrowse() {
        // 初始化视频数量选择器
        SpinnerValueFactory<Integer> valueFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 10);
        videoCountSpinner.setValueFactory(valueFactory);

        // 初始化关键词输入框
        keywordsField.setText("幸福,女性,独立,情感");

        // 初始化表格列
        colIndex.setCellValueFactory(data -> 
                new SimpleStringProperty(String.valueOf(data.getValue().getVideoIndex())));
        colText.setCellValueFactory(data -> 
                new SimpleStringProperty(data.getValue().getTextPreview()));
        colKeywords.setCellValueFactory(data -> {
            List<String> keywords = data.getValue().getMatchedKeywords();
            return new SimpleStringProperty(keywords != null ? String.join(", ", keywords) : "");
        });
        colLiked.setCellValueFactory(data -> 
                new SimpleStringProperty(data.getValue().isLiked() ? "✓" : "-"));
        colStatus.setCellValueFactory(data -> 
                new SimpleStringProperty(data.getValue().getMessage()));

        browseResultTable.setItems(browseResultData);

        // 设置表格行样式
        browseResultTable.setRowFactory(tv -> new TableRow<VideoScanResult>() {
            @Override
            protected void updateItem(VideoScanResult item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isLiked()) {
                    setStyle("-fx-background-color: #f0f9eb;"); // 浅绿色
                } else {
                    setStyle("");
                }
            }
        });
    }

    /**
     * 截图识别
     */
    @FXML
    public void startScreenshot() {
        Platform.runLater(() -> {
            try {
                // 隐藏主窗口
                Stage mainStage = (Stage) screenshotBtn.getScene().getWindow();
                mainStage.setIconified(true);

                // 延迟执行截图，等待窗口隐藏
                new Thread(() -> {
                    try {
                        Thread.sleep(300);
                        Platform.runLater(() -> showScreenshotOverlay(mainStage));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            } catch (Exception e) {
                log.error("启动截图失败", e);
                showError("截图失败: " + e.getMessage());
            }
        });
    }

    /**
     * 显示截图遮罩
     */
    private void showScreenshotOverlay(Stage mainStage) {
        try {
            // 截取全屏
            BufferedImage fullScreen = ScreenshotUtil.captureFullScreen();
            if (fullScreen == null) {
                mainStage.setIconified(false);
                showError("截图失败");
                return;
            }

            Image fxImage = SwingFXUtils.toFXImage(fullScreen, null);

            // 创建截图选择窗口
            Stage overlayStage = new Stage();
            overlayStage.initStyle(StageStyle.TRANSPARENT);
            overlayStage.setAlwaysOnTop(true);

            // 获取屏幕尺寸
            Rectangle2D screenBounds = Screen.getPrimary().getBounds();
            double screenWidth = screenBounds.getWidth();
            double screenHeight = screenBounds.getHeight();

            // 创建画布
            Canvas canvas = new Canvas(screenWidth, screenHeight);
            GraphicsContext gc = canvas.getGraphicsContext2D();

            // 绘制截图背景
            gc.drawImage(fxImage, 0, 0, screenWidth, screenHeight);
            // 添加半透明遮罩
            gc.setFill(Color.rgb(0, 0, 0, 0.3));
            gc.fillRect(0, 0, screenWidth, screenHeight);

            // 选择区域变量
            final double[] startX = {0};
            final double[] startY = {0};
            final double[] endX = {0};
            final double[] endY = {0};
            final boolean[] isDragging = {false};

            // 鼠标按下
            canvas.setOnMousePressed(e -> {
                startX[0] = e.getX();
                startY[0] = e.getY();
                isDragging[0] = true;
            });

            // 鼠标拖动
            canvas.setOnMouseDragged(e -> {
                if (!isDragging[0]) return;

                endX[0] = e.getX();
                endY[0] = e.getY();

                // 重绘
                gc.drawImage(fxImage, 0, 0, screenWidth, screenHeight);
                gc.setFill(Color.rgb(0, 0, 0, 0.3));
                gc.fillRect(0, 0, screenWidth, screenHeight);

                // 绘制选择框
                double x = Math.min(startX[0], endX[0]);
                double y = Math.min(startY[0], endY[0]);
                double w = Math.abs(endX[0] - startX[0]);
                double h = Math.abs(endY[0] - startY[0]);

                // 清除选择区域的遮罩，显示原图
                gc.drawImage(fxImage, x, y, w, h, x, y, w, h);

                // 绘制边框
                gc.setStroke(Color.rgb(64, 158, 255));
                gc.setLineWidth(2);
                gc.strokeRect(x, y, w, h);

                // 显示尺寸信息
                gc.setFill(Color.rgb(64, 158, 255));
                gc.fillRect(x, y - 25, 100, 20);
                gc.setFill(Color.WHITE);
                gc.fillText(String.format("%.0f x %.0f", w, h), x + 5, y - 10);
            });

            // 鼠标释放
            canvas.setOnMouseReleased(e -> {
                if (!isDragging[0]) return;
                isDragging[0] = false;

                double x = Math.min(startX[0], endX[0]);
                double y = Math.min(startY[0], endY[0]);
                double w = Math.abs(endX[0] - startX[0]);
                double h = Math.abs(endY[0] - startY[0]);

                if (w > 10 && h > 10) {
                    // 截取选择区域
                    BufferedImage selectedImage = fullScreen.getSubimage(
                            (int) x, (int) y, (int) w, (int) h);

                    overlayStage.close();
                    mainStage.setIconified(false);

                    // 处理截图
                    handleScreenshotResult(selectedImage);
                }
            });

            // ESC取消
            Pane root = new StackPane(canvas);
            Scene scene = new Scene(root, screenWidth, screenHeight);
            scene.setFill(Color.TRANSPARENT);
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    overlayStage.close();
                    mainStage.setIconified(false);
                }
            });

            overlayStage.setScene(scene);
            overlayStage.setX(0);
            overlayStage.setY(0);
            overlayStage.show();

        } catch (Exception e) {
            log.error("显示截图遮罩失败", e);
            mainStage.setIconified(false);
            showError("截图失败: " + e.getMessage());
        }
    }

    /**
     * 处理截图结果
     */
    private void handleScreenshotResult(BufferedImage image) {
        currentImage = image;
        previewImageView.setImage(SwingFXUtils.toFXImage(image, null));
        
        if (appConfig.isAutoRecognize()) {
            recognizeImage(image, "screenshot");
        }
    }

    /**
     * 从文件识别
     */
    @FXML
    public void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择图片文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        File file = fileChooser.showOpenDialog(fileBtn.getScene().getWindow());
        if (file != null) {
            try {
                BufferedImage image = ImageIO.read(file);
                currentImage = image;
                previewImageView.setImage(SwingFXUtils.toFXImage(image, null));
                recognizeImage(image, "file");
            } catch (Exception e) {
                log.error("读取图片文件失败", e);
                showError("读取图片失败: " + e.getMessage());
            }
        }
    }

    /**
     * 从剪贴板识别
     */
    @FXML
    public void recognizeClipboard() {
        BufferedImage image = ClipboardUtil.getImage();
        if (image != null) {
            currentImage = image;
            previewImageView.setImage(SwingFXUtils.toFXImage(image, null));
            recognizeImage(image, "clipboard");
        } else {
            showError("剪贴板中没有图片");
        }
    }

    /**
     * 执行OCR识别
     */
    private void recognizeImage(BufferedImage image, String type) {
        setLoading(true, "正在识别...");

        Task<OcrResult> task = new Task<>() {
            @Override
            protected OcrResult call() {
                return ocrService.recognize(image);
            }
        };

        task.setOnSucceeded(e -> {
            OcrResult result = task.getValue();
            setLoading(false, null);

            if (result.isSuccess()) {
                resultTextArea.setText(result.getText());
                statusLabel.setText(String.format("识别完成，耗时 %d ms", result.getProcessTime()));

                // 保存历史记录
                historyService.saveHistory(result.getText(), result.getSourcePath(), type);
                loadHistory();

                // 自动复制
                if (autoCopyCheckBox.isSelected()) {
                    ClipboardUtil.copyText(result.getText());
                    statusLabel.setText(statusLabel.getText() + " (已复制)");
                }
            } else {
                showError("识别失败: " + result.getErrorMessage());
            }
        });

        task.setOnFailed(e -> {
            setLoading(false, null);
            showError("识别失败: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    /**
     * 复制结果到剪贴板
     */
    @FXML
    public void copyResult() {
        String text = resultTextArea.getText();
        if (text != null && !text.isEmpty()) {
            ClipboardUtil.copyText(text);
            statusLabel.setText("已复制到剪贴板");
        }
    }

    /**
     * AI优化文本
     */
    @FXML
    public void optimizeText() {
        String text = resultTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            showError("没有可优化的文本");
            return;
        }

        setLoading(true, "AI优化中...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return aiService.optimizeText(text);
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false, null);
            resultTextArea.setText(task.getValue());
            statusLabel.setText("AI优化完成");
        });

        task.setOnFailed(e -> {
            setLoading(false, null);
            showError("AI优化失败: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    /**
     * 翻译文本
     */
    @FXML
    public void translateText() {
        String text = resultTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            showError("没有可翻译的文本");
            return;
        }

        String targetLang = translateLangCombo.getValue();
        setLoading(true, "翻译中...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return aiService.translate(text, targetLang);
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false, null);
            resultTextArea.setText(task.getValue());
            statusLabel.setText("翻译完成 -> " + targetLang);
        });

        task.setOnFailed(e -> {
            setLoading(false, null);
            showError("翻译失败: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    /**
     * 搜索历史记录
     */
    @FXML
    public void searchHistory() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.trim().isEmpty()) {
            loadHistory();
        } else {
            List<OcrHistory> results = historyService.searchHistory(keyword, 1, 50);
            updateHistoryList(results);
        }
    }

    /**
     * 加载历史记录
     */
    private void loadHistory() {
        List<OcrHistory> histories = historyService.getHistoryList(1, 50);
        updateHistoryList(histories);
    }

    /**
     * 更新历史记录列表
     */
    private void updateHistoryList(List<OcrHistory> histories) {
        historyData.clear();
        historyData.addAll(histories);

        ObservableList<String> items = FXCollections.observableArrayList();
        for (OcrHistory h : histories) {
            String preview = h.getText();
            if (preview != null && preview.length() > 30) {
                preview = preview.substring(0, 30) + "...";
            }
            String time = h.getCreateTime() != null ? h.getCreateTime().format(DATE_FORMATTER) : "";
            items.add(String.format("[%s] %s", time, preview));
        }
        historyListView.setItems(items);
    }

    /**
     * 点击历史记录
     */
    @FXML
    public void onHistoryClick(MouseEvent event) {
        int index = historyListView.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < historyData.size()) {
            OcrHistory history = historyData.get(index);
            resultTextArea.setText(history.getText());
        }
    }

    /**
     * 清空历史记录
     */
    @FXML
    public void clearHistory() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认");
        alert.setHeaderText("确定要清空所有历史记录吗？");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                historyService.clearAllHistory();
                loadHistory();
                statusLabel.setText("历史记录已清空");
            }
        });
    }

    /**
     * 设置加载状态
     */
    private void setLoading(boolean loading, String message) {
        Platform.runLater(() -> {
            progressBar.setVisible(loading);
            screenshotBtn.setDisable(loading);
            fileBtn.setDisable(loading);
            clipboardBtn.setDisable(loading);
            if (message != null) {
                statusLabel.setText(message);
            }
        });
    }

    /**
     * 显示错误信息
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("错误: " + message);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // ==================== 抖音刷视频功能 ====================

    /**
     * 检测设备连接状态
     */
    @FXML
    public void checkDevice() {
        boolean connected = adbUtil.isDeviceConnected();
        Platform.runLater(() -> {
            if (connected) {
                deviceStatusLabel.setText("设备状态: 已连接");
                deviceStatusLabel.setStyle("-fx-text-fill: #67c23a;");
            } else {
                deviceStatusLabel.setText("设备状态: 未连接");
                deviceStatusLabel.setStyle("-fx-text-fill: #f56c6c;");
            }
        });
    }

    /**
     * 开始刷抖音视频
     */
    @FXML
    public void startDouyinBrowse() {
        // 检查设备连接
        if (!adbUtil.isDeviceConnected()) {
            showError("设备未连接，请先连接ADB设备");
            return;
        }

        // 获取参数
        int videoCount = videoCountSpinner.getValue();
        String keywordsText = keywordsField.getText();
        List<String> keywords = Arrays.stream(keywordsText.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (keywords.isEmpty()) {
            keywords = douyinBrowseService.getDefaultKeywords();
        }

        // 清空之前的结果
        browseResultData.clear();
        totalLikeCount = 0;
        updateBrowseProgress(0, videoCount);

        // 更新UI状态
        startBrowseBtn.setDisable(true);
        stopBrowseBtn.setDisable(false);
        browseProgressBar.setVisible(true);
        browseProgressBar.setProgress(0);
        statusLabel.setText("正在刷视频...");

        // 启动后台任务
        final List<String> finalKeywords = keywords;
        final int finalVideoCount = videoCount;

        Task<Void> browseTask = new Task<>() {
            @Override
            protected Void call() {
                douyinBrowseService.startBrowsing(finalVideoCount, finalKeywords, result -> {
                    Platform.runLater(() -> {
                        browseResultData.add(result);
                        if (result.isLiked()) {
                            totalLikeCount++;
                        }
                        updateBrowseProgress(result.getVideoIndex(), finalVideoCount);
                        browseProgressBar.setProgress((double) result.getVideoIndex() / finalVideoCount);
                        
                        // 滚动到最后一行
                        browseResultTable.scrollTo(browseResultData.size() - 1);
                    });
                });
                return null;
            }
        };

        browseTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                startBrowseBtn.setDisable(false);
                stopBrowseBtn.setDisable(true);
                browseProgressBar.setVisible(false);
                statusLabel.setText(String.format("刷视频完成！共扫描 %d 个视频，点赞 %d 个", finalVideoCount, totalLikeCount));
            });
        });

        browseTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                startBrowseBtn.setDisable(false);
                stopBrowseBtn.setDisable(true);
                browseProgressBar.setVisible(false);
                showError("刷视频失败: " + browseTask.getException().getMessage());
            });
        });

        new Thread(browseTask).start();
        log.info("开始刷抖音视频，目标数量: {}, 关键词: {}", videoCount, keywords);
    }

    /**
     * 停止刷视频
     */
    @FXML
    public void stopDouyinBrowse() {
        douyinBrowseService.stopBrowsing();
        startBrowseBtn.setDisable(false);
        stopBrowseBtn.setDisable(true);
        browseProgressBar.setVisible(false);
        statusLabel.setText("已停止刷视频");
        log.info("用户停止刷视频");
    }

    /**
     * 清空刷视频结果
     */
    @FXML
    public void clearBrowseResults() {
        browseResultData.clear();
        totalLikeCount = 0;
        browseProgressLabel.setText("进度: 0/0");
        likeCountLabel.setText("点赞: 0");
        statusLabel.setText("已清空刷视频记录");
    }

    /**
     * 更新刷视频进度
     */
    private void updateBrowseProgress(int current, int total) {
        Platform.runLater(() -> {
            browseProgressLabel.setText(String.format("进度: %d/%d", current, total));
            likeCountLabel.setText("点赞: " + totalLikeCount);
        });
    }
}
