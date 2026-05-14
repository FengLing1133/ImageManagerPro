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

    // ==================== FXML 绑定控件 ====================

    /** 显示图片的 ImageView 控件 */
    @FXML
    private ImageView slideImageView;

    /** 显示当前页码/总页数的标签 */
    @FXML
    private Label pageLabel;

    /** 图片容器，用于承载 ImageView 并支持缩放和平移操作 */
    @FXML
    private StackPane stackPane;

    // ==================== 业务状态字段 ====================

    /** 图片文件路径列表，由外部通过 setImagePaths() 设置 */
    private List<String> imagePaths;

    /** 当前显示的图片在 imagePaths 中的索引，范围 [0, imagePaths.size()-1] */
    private int currentIndex = 0;

    /** 基础缩放比例，由 fitImageToWindow() 根据窗口和图片尺寸计算得出 */
    private double baseScale = 1.0;

    /** 用户交互产生的缩放倍数，相对于 baseScale 的倍率，范围 [0.1, 10.0] */
    private double zoomScale = 1.0;

    /** 自动播放的定时器，每秒触发一次 nextImage() */
    private Timeline playTimeline;

    // ==================== 拖拽平移相关字段 ====================

    /** 鼠标拖拽起始位置的 X 坐标（场景坐标系） */
    private double dragStartX = 0;

    /** 鼠标拖拽起始位置的 Y 坐标（场景坐标系） */
    private double dragStartY = 0;

    /** 拖拽开始时 ImageView 的 translateX 值 */
    private double imageStartTranslateX = 0;

    /** 拖拽开始时 ImageView 的 translateY 值 */
    private double imageStartTranslateY = 0;

    /**
     * 控制器初始化方法，由 FXMLLoader 自动调用
     * <p>
     * 完成以下初始化工作：
     * 1. 配置 ImageView 的显示属性（保持宽高比、平滑处理）
     * 2. 创建自动播放定时器（1秒间隔，无限循环）
     * 3. 监听容器尺寸变化，实现窗口自适应
     * 4. 绑定鼠标滚轮事件，实现缩放功能
     * 5. 绑定鼠标拖拽事件，实现平移功能
     * </p>
     */
    @FXML
    public void initialize() {
        // 配置 ImageView 显示属性
        slideImageView.setPreserveRatio(true);   // 保持图片宽高比，防止变形
        slideImageView.setSmooth(true);          // 启用平滑处理，提升缩放后的图片质量
        slideImageView.setCache(false);          // 禁用缓存，避免大图片占用过多内存

        // 创建自动播放定时器：每1秒触发一次 nextImage()，无限循环
        playTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> nextImage()));
        playTimeline.setCycleCount(Timeline.INDEFINITE);

        // 监听容器尺寸变化，窗口大小改变时自动重新计算图片适应尺寸
        stackPane.widthProperty().addListener((obs, oldVal, newVal) -> fitImageToWindow());
        stackPane.heightProperty().addListener((obs, oldVal, newVal) -> fitImageToWindow());

        // 绑定鼠标滚轮事件：实现图片缩放功能
        // 向上滚动放大（每次 1.05 倍），向下滚动缩小（每次 /1.05），缩放范围 [0.1, 10.0]
        stackPane.setOnScroll(event -> {
            if (slideImageView.getImage() == null) return;
            double delta = event.getDeltaY();
            if (delta > 0) {
                zoomScale = Math.min(zoomScale * 1.05, 10.0);   // 放大，最大 10 倍
            } else {
                zoomScale = Math.max(zoomScale / 1.05, 0.1);    // 缩小，最小 0.1 倍
            }
            // 最终尺寸 = 原图尺寸 × 基础缩放 × 用户缩放
            double finalScale = baseScale * zoomScale;
            slideImageView.setFitWidth(slideImageView.getImage().getWidth() * finalScale);
            slideImageView.setFitHeight(slideImageView.getImage().getHeight() * finalScale);
            // 注意：缩放时不重置平移位置，保持用户当前的视图位置
        });

        // 绑定鼠标按下事件：记录拖拽起始点
        stackPane.setOnMousePressed(event -> {
            if (slideImageView.getImage() == null) return;
            dragStartX = event.getSceneX();                              // 记录鼠标在场景坐标系中的起始 X
            dragStartY = event.getSceneY();                              // 记录鼠标在场景坐标系中的起始 Y
            imageStartTranslateX = slideImageView.getTranslateX();       // 记录图片当前的平移偏移量 X
            imageStartTranslateY = slideImageView.getTranslateY();       // 记录图片当前的平移偏移量 Y
        });

        // 绑定鼠标拖拽事件：实现图片平移功能
        stackPane.setOnMouseDragged(event -> {
            if (slideImageView.getImage() == null) return;
            double offsetX = event.getSceneX() - dragStartX;  // 计算鼠标水平偏移量
            double offsetY = event.getSceneY() - dragStartY;  // 计算鼠标垂直偏移量
            // 新的平移位置 = 拖拽开始时的位置 + 鼠标偏移量
            slideImageView.setTranslateX(imageStartTranslateX + offsetX);
            slideImageView.setTranslateY(imageStartTranslateY + offsetY);
        });
    }

    /**
     * 将图片自适应窗口大小
     * <p>
     * 计算逻辑：
     * 1. 获取容器可用尺寸（减去边距）
     * 2. 计算图片宽高比与容器宽高比
     * 3. 取较小的缩放比例，确保图片完全显示在容器内
     * 4. 只缩小不放大（scale ≤ 1.0），小图片保持原始尺寸
     * 5. 重置缩放和平移状态
     * </p>
     */
    private void fitImageToWindow() {
        Image image = slideImageView.getImage();
        if (image == null) return;

        double windowWidth = stackPane.getWidth();      // 获取容器宽度
        double windowHeight = stackPane.getHeight();     // 获取容器高度
        if (windowWidth <= 0 || windowHeight <= 0) return;

        double padding = 40.0;                                          // 图片与容器边缘的间距（像素）
        double availableWidth = Math.max(windowWidth - padding, 1.0);   // 可用显示宽度，最小 1 像素
        double availableHeight = Math.max(windowHeight - padding, 1.0); // 可用显示高度，最小 1 像素

        double imgWidth = image.getWidth();     // 原图宽度
        double imgHeight = image.getHeight();   // 原图高度

        // 计算缩放比例：取宽度和高度方向的较小值，且不超过 1.0（只缩小不放大）
        double scale = Math.min(1.0, Math.min(availableWidth / imgWidth, availableHeight / imgHeight));
        baseScale = scale;    // 更新基础缩放比例
        zoomScale = 1.0;      // 重置用户缩放倍数

        slideImageView.setFitWidth(imgWidth * scale);    // 设置缩放后的宽度
        slideImageView.setFitHeight(imgHeight * scale);   // 设置缩放后的高度
        slideImageView.setTranslateX(0);                  // 重置水平平移
        slideImageView.setTranslateY(0);                  // 重置垂直平移
    }

    /**
     * 设置图片路径列表
     * <p>
     * 由主控制器调用，传入当前目录下的所有图片路径。
     * 设置后会自动重置到第一张图片并开始显示。
     * </p>
     * @param imagePaths 图片文件路径列表，可以为 null 或空列表
     */
    public void setImagePaths(List<String> imagePaths) {
        // 防御性拷贝，避免外部修改影响内部状态
        this.imagePaths = imagePaths == null ? null : new ArrayList<>(imagePaths);
        this.currentIndex = 0;
        if (this.imagePaths != null && !this.imagePaths.isEmpty()) {
            pageLabel.setText((currentIndex + 1) + "/" + this.imagePaths.size());  // 显示 "1/总数"
            loadImage(this.imagePaths.get(currentIndex));  // 加载第一张图片
        } else {
            pageLabel.setText("0/0");       // 无图片时显示 "0/0"
            slideImageView.setImage(null);  // 清空图片显示
        }
    }

    /**
     * 设置当前显示的图片索引
     * <p>
     * 用于外部跳转到指定图片，索引会被限制在有效范围内 [0, size-1]。
     * </p>
     * @param index 目标图片索引，会被自动边界保护
     */
    public void setCurrentIndex(int index) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return;
        }
        // 边界保护：确保索引在 [0, imagePaths.size()-1] 范围内
        this.currentIndex = Math.max(0, Math.min(index, imagePaths.size() - 1));
        loadImage(imagePaths.get(currentIndex));
        pageLabel.setText((currentIndex + 1) + "/" + imagePaths.size());
    }

    /**
     * 加载并显示指定路径的图片
     * <p>
     * 使用异步方式加载图片，避免大图片阻塞 UI 线程。
     * 加载完成后自动调用 fitImageToWindow() 适应窗口大小。
     * 加载失败时显示错误提示对话框。
     * </p>
     * @param imagePath 图片文件的绝对路径
     */
    private void loadImage(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            // 检查文件是否存在且是文件（非目录）
            if (!imageFile.exists() || !imageFile.isFile()) {
                AlterUtil.showAlert(
                    Alert.AlertType.ERROR,
                    "图片加载失败",
                    "文件不存在：" + imagePath,
                    stackPane != null && stackPane.getScene() != null ? stackPane.getScene().getWindow() : null
                );
                return;
            }

            // 异步加载图片（第二个参数 true 表示后台加载）
            Image image = new Image(imageFile.toURI().toString(), true);

            // 监听加载进度，加载完成后处理
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0) {  // 加载完成
                    if (image.isError()) {
                        // 图片解码失败
                        AlterUtil.showAlert(
                            Alert.AlertType.ERROR,
                            "图片加载失败",
                            "解码错误：" + imagePath,
                            stackPane != null && stackPane.getScene() != null ? stackPane.getScene().getWindow() : null
                        );
                        if (image.getException() != null) {
                            image.getException().printStackTrace();
                        }
                    } else {
                        // 加载成功：设置图片、重置缩放、适应窗口
                        slideImageView.setImage(image);
                        zoomScale = 1.0;
                        fitImageToWindow();
                    }
                }
            });

            // 监听加载异常（如文件格式不支持）
            image.exceptionProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    AlterUtil.showAlert(
                        Alert.AlertType.ERROR,
                        "图片加载失败",
                        "加载图片失败：" + imagePath,
                        stackPane != null && stackPane.getScene() != null ? stackPane.getScene().getWindow() : null
                    );
                    newVal.printStackTrace();
                }
            });
        } catch (Exception e) {
            // 捕获同步代码中的异常（如文件路径无效）
            AlterUtil.showAlert(
                Alert.AlertType.ERROR,
                "图片加载失败",
                "加载图片失败：" + imagePath,
                stackPane != null && stackPane.getScene() != null ? stackPane.getScene().getWindow() : null
            );
            e.printStackTrace();
        }
    }

    /**
     * 切换到上一张图片
     * <p>
     * 使用取模运算实现循环浏览：当到达第一张时，跳转到最后一张。
     * </p>
     */
    @FXML
    public void prevImage() {
        if (imagePaths == null || imagePaths.isEmpty()) return;
        // 循环索引：(currentIndex - 1 + size) % size，避免负数
        currentIndex = (currentIndex - 1 + imagePaths.size()) % imagePaths.size();
        loadImage(imagePaths.get(currentIndex));
        pageLabel.setText((currentIndex + 1) + "/" + imagePaths.size());
    }

    /**
     * 切换到下一张图片
     * <p>
     * 使用取模运算实现循环浏览：当到达最后一张时，跳转到第一张。
     * 同时也被自动播放定时器调用。
     * </p>
     */
    @FXML
    public void nextImage() {
        if (imagePaths == null || imagePaths.isEmpty()) return;
        // 循环索引：(currentIndex + 1) % size，到达末尾时回到 0
        currentIndex = (currentIndex + 1) % imagePaths.size();
        loadImage(imagePaths.get(currentIndex));
        pageLabel.setText((currentIndex + 1) + "/" + imagePaths.size());
    }

    /**
     * 放大图片
     * <p>
     * 调用时会停止自动播放，避免用户操作与自动切换冲突。
     * 放大倍数为 1.1 倍，最大不超过 5 倍。
     * </p>
     */
    @FXML
    public void zoomIn() {
        if (playTimeline != null) playTimeline.stop();  // 缩放时停止自动播放
        zoomScale = Math.min(zoomScale * 1.1, 5.0);     // 放大，最大 5 倍
        slideImageView.setFitWidth(slideImageView.getFitWidth() * zoomScale);
        slideImageView.setFitHeight(slideImageView.getFitHeight() * zoomScale);
        // 注意：缩放时不重置平移，保持用户当前的视图位置
    }

    /**
     * 缩小图片
     * <p>
     * 调用时会停止自动播放，避免用户操作与自动切换冲突。
     * 缩小倍数为 1/1.1 倍，最小不小于 0.1 倍。
     * </p>
     */
    @FXML
    public void zoomOut() {
        if (playTimeline != null) playTimeline.stop();  // 缩放时停止自动播放
        zoomScale = Math.max(zoomScale / 1.1, 0.1);     // 缩小，最小 0.1 倍
        slideImageView.setFitWidth(slideImageView.getFitWidth() / zoomScale);
        slideImageView.setFitHeight(slideImageView.getFitHeight() / zoomScale);
        // 注意：缩放时不重置平移，保持用户当前的视图位置
    }

    /**
     * 开始自动播放
     * <p>
     * 启动定时器，每秒自动切换到下一张图片。
     * 如果图片列表为空或定时器未初始化，则不执行操作。
     * </p>
     */
    @FXML
    public void startPlay() {
        if (imagePaths == null || imagePaths.isEmpty() || playTimeline == null) return;
        playTimeline.play();  // 启动自动播放定时器
    }

    /**
     * 停止自动播放
     * <p>
     * 停止定时器，图片停留在当前显示的图片上。
     * </p>
     */
    @FXML
    public void stopPlay() {
        if (playTimeline != null) {
            playTimeline.stop();  // 停止自动播放定时器
        }
    }

    /**
     * 阻止鼠标事件冒泡
     * <p>
     * 用于防止缩放操作时触发父容器的其他事件处理。
     * </p>
     *
     * @param event 鼠标事件
     */
    @FXML
    private void stopZooming(javafx.scene.input.MouseEvent event) {
        event.consume();  // 消费事件，阻止向父节点传播
    }
}
