package com.yang.controller;

import org.springframework.stereotype.Component;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.yang.util.AlterUtil;
import javafx.scene.control.Alert;

/**
 * 幻灯片播放控制器
 * <p>
 * 提供图片幻灯片播放功能，支持：
 * - 图片浏览（上一张/下一张）
 * - 自动播放（定时切换）
 * - 鼠标滚轮缩放
 * - 鼠标拖拽平移
 * - 自适应窗口大小
 * </p>
 */
@Component
public class SlideShowController {

    @FXML private ImageView slideImageView;
    @FXML private Label pageLabel;
    @FXML private StackPane stackPane;

    private List<String> imagePaths;    // 图片路径列表
    private int currentIndex = 0;       // 当前显示的图片索引
    private double baseScale = 1.0;     // 自适应窗口的基础缩放比
    private double zoomScale = 1.0;     // 用户手动缩放倍率
    private Timeline playTimeline;      // 自动播放定时器

    // 拖拽平移相关状态
    private double dragStartX = 0;
    private double dragStartY = 0;
    private double imageStartTranslateX = 0;
    private double imageStartTranslateY = 0;

    /** 由 FXMLLoader 自动调用，初始化控件配置、事件绑定和自动播放定时器 */
    @FXML
    public void initialize() {
        slideImageView.setPreserveRatio(true); // 保持图片宽高比
        slideImageView.setSmooth(true);  // 启用平滑缩放
        slideImageView.setCache(false);  // 禁用缓存，避免内存泄漏和过多内存占用
        playTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> nextImage()));
        playTimeline.setCycleCount(Timeline.INDEFINITE);
        stackPane.widthProperty().addListener((obs, oldVal, newVal) -> fitImageToWindow());
        stackPane.heightProperty().addListener((obs, oldVal, newVal) -> fitImageToWindow());
        // 滚轮缩放：上滚放大，下滚缩小，范围 [0.1, 10.0]
        stackPane.setOnScroll(event -> {
            if (slideImageView.getImage() == null) return;
            double delta = event.getDeltaY();
            if (delta > 0) {
                zoomScale = Math.min(zoomScale * 1.05, 10.0);
            } else {
                zoomScale = Math.max(zoomScale / 1.05, 0.1);
            }
            applyZoom();
        });
        // 鼠标拖拽平移
        stackPane.setOnMousePressed(event -> {
            if (slideImageView.getImage() == null) return;
            dragStartX = event.getSceneX();
            dragStartY = event.getSceneY();
            imageStartTranslateX = slideImageView.getTranslateX();
            imageStartTranslateY = slideImageView.getTranslateY();
        });
        stackPane.setOnMouseDragged(event -> {
            if (slideImageView.getImage() == null) return;
            slideImageView.setTranslateX(imageStartTranslateX + event.getSceneX() - dragStartX);
            slideImageView.setTranslateY(imageStartTranslateY + event.getSceneY() - dragStartY);
        });
    }

    /** 将图片自适应窗口大小，只缩小不放大，同时重置缩放和平移状态 */
    private void fitImageToWindow() {
        Image image = slideImageView.getImage();
        if (image == null) return;

        double windowWidth = stackPane.getWidth();
        double windowHeight = stackPane.getHeight();
        if (windowWidth <= 0 || windowHeight <= 0) return;

        double padding = 40.0;
        double availableWidth = Math.max(windowWidth - padding, 1.0);
        double availableHeight = Math.max(windowHeight - padding, 1.0);

        double imgWidth = image.getWidth();
        double imgHeight = image.getHeight();

        // 只缩小不放大，取宽高方向较小的缩放比
        double scale = Math.min(1.0, Math.min(availableWidth / imgWidth, availableHeight / imgHeight));
        baseScale = scale;
        zoomScale = 1.0;

        slideImageView.setFitWidth(imgWidth * scale);
        slideImageView.setFitHeight(imgHeight * scale);
        slideImageView.setTranslateX(0);
        slideImageView.setTranslateY(0);
    }

    /** 设置图片路径列表，自动重置到第一张并显示 */
    public void setImagePaths(List<String> imagePaths) {
        this.imagePaths = imagePaths == null ? null : new ArrayList<>(imagePaths);
        this.currentIndex = 0;
        if (this.imagePaths != null && !this.imagePaths.isEmpty()) {
            pageLabel.setText((currentIndex + 1) + "/" + this.imagePaths.size());
            loadImage(this.imagePaths.get(currentIndex));
        } else {
            pageLabel.setText("0/0");
            slideImageView.setImage(null);
        }
    }

    /** 设置当前图片索引，自动边界保护 */
    public void setCurrentIndex(int index) {
        if (imagePaths == null || imagePaths.isEmpty()) return;
        this.currentIndex = Math.max(0, Math.min(index, imagePaths.size() - 1));
        loadImage(imagePaths.get(currentIndex));
        pageLabel.setText((currentIndex + 1) + "/" + imagePaths.size());
    }

    /** 加载图片，加载成功后自适应窗口，失败时弹出错误提示 */
    private void loadImage(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists() || !imageFile.isFile()) {
                AlterUtil.showAlert(Alert.AlertType.ERROR, "图片加载失败",
                        "文件不存在：" + imagePath, getWindow());
                return;
            }

            // 同步加载图片，避免 JavaFX 缓存导致 progress 监听器不触发的问题
            Image image = new Image(imageFile.toURI().toString(), false);

            if (image.isError()) {
                AlterUtil.showAlert(Alert.AlertType.ERROR, "图片加载失败",
                        "解码错误：" + imagePath, getWindow());
                if (image.getException() != null) image.getException().printStackTrace();
                return;
            }

            slideImageView.setImage(image);
            zoomScale = 1.0;
            fitImageToWindow();
        } catch (Exception e) {
            AlterUtil.showAlert(Alert.AlertType.ERROR, "图片加载失败",
                    "加载图片失败：" + imagePath, getWindow());
            e.printStackTrace();
        }
    }

    /** 获取当前窗口引用，用于 Alert 弹窗的 owner */
    private javafx.stage.Window getWindow() {
        return stackPane != null && stackPane.getScene() != null ? stackPane.getScene().getWindow() : null;
    }

    /** 切换到上一张图片（循环） */
    @FXML
    public void prevImage() {
        if (imagePaths == null || imagePaths.isEmpty()) return;
        currentIndex = (currentIndex - 1 + imagePaths.size()) % imagePaths.size();
        loadImage(imagePaths.get(currentIndex));
        pageLabel.setText((currentIndex + 1) + "/" + imagePaths.size());
    }

    /** 切换到下一张图片（循环） */
    @FXML
    public void nextImage() {
        if (imagePaths == null || imagePaths.isEmpty()) return;
        currentIndex = (currentIndex + 1) % imagePaths.size();
        loadImage(imagePaths.get(currentIndex));
        pageLabel.setText((currentIndex + 1) + "/" + imagePaths.size());
    }

    /** 放大图片，同时停止自动播放 */
    @FXML
    public void zoomIn() {
        if (playTimeline != null) playTimeline.stop();
        zoomScale = Math.min(zoomScale * 1.1, 10.0);
        applyZoom();
    }

    /** 缩小图片，同时停止自动播放 */
    @FXML
    public void zoomOut() {
        if (playTimeline != null) playTimeline.stop();
        zoomScale = Math.max(zoomScale / 1.1, 0.1);
        applyZoom();
    }

    /** 根据 baseScale 和 zoomScale 重新计算图片显示尺寸 */
    private void applyZoom() {
        Image image = slideImageView.getImage();
        if (image == null) return;
        double finalScale = baseScale * zoomScale;
        slideImageView.setFitWidth(image.getWidth() * finalScale);
        slideImageView.setFitHeight(image.getHeight() * finalScale);
    }

    /** 启动自动播放 */
    @FXML
    public void startPlay() {
        if (imagePaths == null || imagePaths.isEmpty() || playTimeline == null) return;
        playTimeline.play();
    }

    /** 停止自动播放 */
    @FXML
    public void stopPlay() {
        if (playTimeline != null) playTimeline.stop();
    }

    /** 消费鼠标事件，防止缩放按钮触发缩放操作 */
    @FXML
    private void stopZooming(javafx.scene.input.MouseEvent event) {
        event.consume();
    }
}
