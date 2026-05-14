package com.yang.controller;

import org.springframework.stereotype.Component;
import com.yang.repository.FileRepository;
import com.yang.service.*;
import com.yang.strategy.RenderStrategy;
import com.yang.util.AlterUtil;
import com.yang.util.VBoxFactory;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.layout.AnchorPane;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

/**
 * 主窗口控制器 —— 应用的核心控制器
 * <p>职责概览：</p>
 * <ul>
 *   <li>管理左侧目录树与右侧文件卡片面板的联动</li>
 *   <li>协调各 Service 完成目录导航、文件加载、图片缩略图展示</li>
 *   <li>处理文件卡片的选中、右键菜单（删除/复制/重命名/粘贴）等交互</li>
 *   <li>实现分批懒加载（初始 120 个，滚动到底再加载 60 个），优化大目录场景性能</li>
 *   <li>通过 loadToken 机制防止并发目录切换导致的竞态问题</li>
 * </ul>
 */
@Component
public class MainController {

    // ==================== FXML 绑定的 UI 组件 ====================

    /** 左侧目录树，展示"我的电脑"→磁盘→文件夹的层级结构 */
    @FXML
    private TreeView<String> dirTreeView;
    /** 包裹 FlowPane 的滚动视口，提供垂直滚动能力 */
    @FXML
    private ScrollPane imageScrollPane;
    /** FlowPane 的父容器，用于在内容不足一屏时撑满视口高度 */
    @FXML
    private AnchorPane imageAnchorPane;
    /** 右侧文件卡片容器，以 FlowPane 流式布局排列文件/图片 VBox 卡片 */
    @FXML
    private FlowPane imageFlowPane;
    /** 底部状态栏标签，显示当前目录名、图片数量、总大小、选中信息 */
    @FXML
    private Label tipLabel;
    /** 顶部路径输入框，显示当前目录路径，支持手动输入并回车跳转 */
    @FXML
    private TextField pathField;
    /** 目录为空时显示的提示标签（"此目录为空"） */
    @FXML
    private Label emptyTipLabel;

    // ==================== 注入的业务服务 ====================

    /** 导航服务：封装目录导航、前进后退历史栈、路径解析等业务逻辑 */
    private final NavigationService navigationService;
    /** 文件操作服务：处理文件的删除、复制、粘贴、重命名等业务逻辑 */
    private final FileOperationService fileOperationService;
    /** 图片服务：封装图片加载、LRU 缓存管理、缩略图生成等业务逻辑 */
    private final ImageService imageService;
    /** 目录服务：封装系统根目录获取、图片目录获取等业务逻辑 */
    private final DirectoryService directoryService;
    /** 文件系统数据访问层：封装所有文件系统读写操作，隔离 IO 细节 */
    private final FileRepository fileRepository;
    /** 渲染策略接口：控制分批构建任务的执行节奏（每批 50 个），实现渐进式渲染 */
    private final RenderStrategy renderStrategy;

    // ==================== UI 工具与状态 ====================

    /** 卡片工厂：负责创建文件 VBox、图片 VBox、快捷方式 VBox 等 UI 组件 */
    private final VBoxFactory vBoxFactory = new VBoxFactory();
    /** 目录树 UI 交互服务，与 TreeView 强耦合，负责树的初始化、展开、选中等操作 */
    private DirectoryTreeService directoryTreeService;

    // ---------- 选中模型 ----------
    /** 当前被选中的 VBox 集合，支持 Ctrl+左键 多选切换 */
    private final Set<VBox> selectedVBoxes = new HashSet<>();
    /** VBox → File 的映射表，用于根据选中的 VBox 反查对应的文件 */
    private final Map<VBox, File> vBoxToFile = new HashMap<>();

    // ---------- 文件统计缓存 ----------
    /** 当前目录下所有可见文件列表（包括文件和文件夹） */
    private final List<File> allFiles = new ArrayList<>();
    /** 文件大小缓存，避免重复调用 file.length() */
    private final Map<File, Long> fileSizeCache = new HashMap<>();
    /** 缓存的图片文件数量，用于状态栏显示 */
    private long cachedImageCount = 0;
    /** 缓存的文件总大小（字节），用于状态栏显示 */
    private long cachedTotalSize = 0;

    // ---------- 分批懒加载机制 ----------
    /**
     * 加载令牌（单调递增），每次进入新目录时 +1。
     * 异步回调中比对此值，若不匹配则说明用户已切换目录，应丢弃本次结果，
     * 从而避免竞态导致的卡片错乱或重复添加。
     */
    private long activeLoadToken = 0;
    /** 首次加载的文件数量上限（非图片优先，图片填充剩余配额） */
    private static final int INITIAL_BATCH_SIZE = 120;
    /** 滚动触底后每批追加加载的文件数量 */
    private static final int LOAD_MORE_BATCH_SIZE = 60;
    /** 尚未加载的图片文件队列（分批懒加载用） */
    private List<File> pendingImageFiles = new ArrayList<>();
    /** 尚未加载的非图片文件队列（分批懒加载用） */
    private List<File> pendingNonImageFiles = new ArrayList<>();
    /** 是否正在加载更多文件的标志位，防止滚动事件重复触发加载 */
    private volatile boolean isLoadingMore = false;
    /** 渲染管道每批构建的卡片数量 */
    private static final int BUILD_BATCH_SIZE = 50;
    /** 待执行的卡片构建任务队列，由 RenderStrategy 分批消费 */
    private final Deque<Runnable> pendingBuildTasks = new ArrayDeque<>();
    /** 下一个卡片应插入的位置，用于保证异步加载的图片按文件顺序排列 */
    private int nextInsertIndex = 0;

    // ---------- 卡片样式常量 ----------
    /** 未选中状态的 VBox 样式：浅灰边框 + 白色背景 + 圆角 */
    private static final String NORMAL_STYLE = "-fx-alignment: center; -fx-border-color: #E5E7EB; -fx-border-width: 1.5px; -fx-background-color: #FFFFFF; -fx-background-radius: 8; -fx-border-radius: 8;";
    /** 选中状态的 VBox 样式：青色边框 + 浅青背景 + 圆角 + 微光阴影 */
    private static final String SELECTED_STYLE = "-fx-alignment: center; -fx-border-color: #06B6D4; -fx-border-width: 2px; -fx-background-color: #ECFEFF; -fx-background-radius: 8; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(6, 182, 212, 0.15), 10, 0, 0, 2);";

    /** 空白区域右键菜单，点击空白处弹出，包含粘贴等操作 */
    private ContextMenu blankContextMenu = null;
    /** 缩略图尺寸（像素），正方形 */
    private static final int THUMB_SIZE = 120;
    /** 悬停动效阈值：文件数超过此值时禁用卡片悬停动画，避免卡顿 */
    private static final int HOVER_EFFECT_THRESHOLD = 500;

    /**
     * 构造器注入所有依赖的 Service 和 Repository。
     * Spring 容器在创建 MainController Bean 时自动注入这些依赖。
     */
    public MainController(NavigationService navigationService,
                          FileOperationService fileOperationService,
                          ImageService imageService,
                          DirectoryService directoryService,
                          FileRepository fileRepository,
                          RenderStrategy renderStrategy) {
        this.navigationService = navigationService;
        this.fileOperationService = fileOperationService;
        this.imageService = imageService;
        this.directoryService = directoryService;
        this.fileRepository = fileRepository;
        this.renderStrategy = renderStrategy;
    }

    /**
     * FXML 初始化方法，在 FXMLLoader.load() 之后自动调用。
     * 负责完成以下初始化工作：
     * 1. 创建目录树服务
     * 2. 初始化目录树（加载"我的电脑"→磁盘层级）
     * 3. 配置单元格工厂和选中监听
     * 4. 配置路径输入框的回车跳转逻辑
     * 5. 在 FlowPane 中显示系统根目录和图片目录的快捷入口
     * 6. 注册视口/内容区域的尺寸变化监听器，保持布局同步
     * 7. 注册滚动监听器，实现触底懒加载
     * 8. 注册右键菜单和鼠标点击事件
     */
    @FXML
    public void initialize() {
        directoryTreeService = new DirectoryTreeService(dirTreeView);
        directoryTreeService.initDirectoryTree();
        setupDirTreeCellFactory();
        setupDirTreeSelectionListener();
        setupPathFieldListener();
        initFlowPaneHint();
        
        // 监听视口尺寸变化：更新 FlowPane/AnchorPane 宽度，并延迟同步 AnchorPane 高度
        imageScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            imageFlowPane.setPrefWidth(newVal.getWidth());
            imageAnchorPane.setPrefWidth(newVal.getWidth());
            Platform.runLater(this::syncAnchorPaneHeight);
        });

        // 监听滚动位置：当滚动到 89% 以上时触发加载更多文件（触底懒加载）
        imageScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            double vvalue = newVal.doubleValue();
            if (vvalue > 0.89 && !isLoadingMore && hasMoreFiles()) {
                loadMoreFiles();
            }
        });

        // ---- 右键菜单：在 AnchorPane 空白区域右键时弹出 ----
        imageAnchorPane.setOnContextMenuRequested(event -> {
            if (event.getTarget() == imageAnchorPane) {
                clearSelection();
                hideBlankContextMenu();
                blankContextMenu = vBoxFactory.buildContextMenu(0, this::deleteSelected, this::copySelected, this::renameSelected, this::pasteFiles);
                blankContextMenu.show(imageAnchorPane, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });

        // ---- 鼠标左键点击空白区域时关闭右键菜单 ----
        imageFlowPane.setOnMousePressed(event -> hideBlankContextMenu());
        imageAnchorPane.setOnMousePressed(event -> { if (event.getButton() == MouseButton.PRIMARY) hideBlankContextMenu(); });
    }

    /** 隐藏空白区域右键菜单（如果正在显示） */
    private void hideBlankContextMenu() {
        if (blankContextMenu != null && blankContextMenu.isShowing()) {
            blankContextMenu.hide();
        }
    }

    /**
     * 配置目录树的单元格工厂。
     * <p>功能：</p>
     * <ul>
     *   <li>根据节点类型显示不同图标（"我的电脑"无图标、磁盘显示硬盘图标、文件夹显示文件夹图标）</li>
     *   <li>单击非根节点时切换展开/折叠状态</li>
     *   <li>设置目录树固定宽度 250px</li>
     * </ul>
     */
    private void setupDirTreeCellFactory() {
        dirTreeView.setCellFactory(tv -> {
            javafx.scene.control.TreeCell<String> cell = new javafx.scene.control.TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty); // 必须调用：通知父类刷新内部状态（选中、文本、graphic），否则单元格渲染异常
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item);
                        javafx.scene.image.ImageView iconView = null;
                        if (getTreeItem().getParent() == null || "我的电脑".equals(item)) {
                            setGraphic(null);
                        } else if (item.matches("^[A-Z]:\\\\$")) {
                            java.net.URL iconUrl = getClass().getResource("/icons/hard-drive.png");
                            if (iconUrl != null) {
                                iconView = new javafx.scene.image.ImageView(iconUrl.toExternalForm());
                            }
                        } else {
                            java.net.URL iconUrl = getClass().getResource("/icons/folder.png");
                            if (iconUrl != null) {
                                iconView = new javafx.scene.image.ImageView(iconUrl.toExternalForm());
                            }
                        }
                        if (iconView != null) {
                            iconView.setFitWidth(18);
                            iconView.setFitHeight(18);
                            setGraphic(iconView);
                        }
                    }
                }
            };
            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() == 1 && cell.getTreeItem() != null) {
                    TreeItem<String> item = cell.getTreeItem();
                    if (!item.getValue().equals("我的电脑")) {
                        item.setExpanded(!item.isExpanded());
                    }
                }
            });
            return cell;
        });
        dirTreeView.setPrefWidth(250);
    }

    /**
     * 配置目录树的选中监听器。
     * 当用户在目录树中选中某个节点时，获取该节点的完整路径并导航到对应目录。
     * 不需要同步树选中状态（因为就是从树触发的）。
     */
    private void setupDirTreeSelectionListener() {
        dirTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                String fullPath = directoryTreeService.getFullPath(newItem);
                File selectedDir = new File(fullPath);
                navigateToDirectory(selectedDir, false);
            }
        });
    }

    /**
     * 导航到指定目录的核心方法。
     * @param dir               目标目录
     * @param syncTreeSelection 是否同步更新左侧目录树的选中状态（从路径输入框或面包屑导航时需要同步）
     */
    private void navigateToDirectory(File dir, boolean syncTreeSelection) {
        if (dir != null && dir.isDirectory()) {
            if (!navigationService.navigateTo(dir)) return;
            pathField.setText(dir.getAbsolutePath());
            loadImagesToFlowPane(dir);
            if (syncTreeSelection) {
                directoryTreeService.expandAndSelectInTree(dir.getAbsolutePath());
            }
        }
    }

    /**
     * 配置路径输入框的回车跳转逻辑。
     * <p>行为：</p>
     * <ul>
     *   <li>初始化时显示当前目录路径</li>
     *   <li>回车后解析输入路径，有效则导航并同步目录树选中</li>
     *   <li>路径无效时输入框变红显示"路径无效"，1 秒后恢复</li>
     * </ul>
     */
    private void setupPathFieldListener() {
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir != null) {
            pathField.setText(currentDir.getAbsolutePath());
        }
        pathField.setOnAction(event -> {
            File resolved = navigationService.resolvePath(pathField.getText());
            if (resolved != null) {
                navigateToDirectory(resolved, true);
            } else {
                pathField.setStyle("-fx-background-color: #fff4f4; -fx-text-fill: #b92d2d; -fx-border-color: #efb4b4; -fx-border-width: 1; -fx-background-radius: 10; -fx-border-radius: 10;");
                pathField.setText("路径无效");
                PauseTransition pause = new PauseTransition(Duration.seconds(1));
                pause.setOnFinished(e -> {
                    File cur = navigationService.getCurrentDirectory();
                    if (cur != null) pathField.setText(cur.getAbsolutePath());
                    pathField.setStyle("");
                });
                pause.play();
            }
        });
    }

    /**
     * 加载指定目录下的所有文件到 FlowPane（核心加载方法）。
     * <p>执行流程：</p>
     * <ol>
     *   <li>递增 loadToken，使之前的异步回调全部失效（防竞态）</li>
     *   <li>停止渲染管道、清空所有状态和 UI</li>
     *   <li>在后台线程扫描目录，分离图片文件和非图片文件，计算统计信息</li>
     *   <li>回到 FX 线程，取 INITIAL_BATCH_SIZE(120) 个文件作为首批加载（非图片优先）</li>
     *   <li>剩余文件存入 pendingImageFiles / pendingNonImageFiles 等待懒加载</li>
     *   <li>为首批文件创建构建任务，交给 RenderStrategy 分批执行</li>
     * </ol>
     *
     * @param dir        要加载的目录
     */
    private void loadImagesToFlowPane(File dir) {
        long loadToken = ++activeLoadToken;
        renderStrategy.stopAll();
        pendingBuildTasks.clear();
        allFiles.clear();
        selectedVBoxes.clear();
        vBoxToFile.clear();
        fileSizeCache.clear();
        pendingImageFiles.clear();
        pendingNonImageFiles.clear();
        isLoadingMore = false;
        imageFlowPane.getChildren().clear();

        imageService.getExecutor().submit(() -> {
            List<File> visibleFiles = fileRepository.listVisibleFiles(dir);

            if (visibleFiles.isEmpty()) {
                Platform.runLater(() -> {
                    if (isStaleLoad(loadToken, dir)) return;// 目录已切换，丢弃结果
                    emptyTipLabel.setVisible(true);
                    emptyTipLabel.setManaged(true);
                    cachedImageCount = 0;
                    cachedTotalSize = 0;
                    updateTipLabel();
                });
                return;
            }

            List<File> imageFiles = new ArrayList<>();
            List<File> nonImageFiles = new ArrayList<>();
            Map<File, Long> localSizeCache = new HashMap<>();
            long imageCount = 0;
            long totalSize = 0;
            for (File file : visibleFiles) {
                if (file.isFile()) {
                    long size = file.length();
                    localSizeCache.put(file, size);
                    totalSize += size;
                }
                if (!file.isDirectory() && imageService.isImageFile(file)) {
                    imageFiles.add(file);
                    imageCount++;
                } else {
                    nonImageFiles.add(file);
                }
            }
            final long finalImageCount = imageCount;
            final long finalTotalSize = totalSize;
            final Map<File, Long> finalSizeCache = localSizeCache;
            final boolean enableHoverEffects = visibleFiles.size() <= HOVER_EFFECT_THRESHOLD;//判断是否启用悬停动画

            Platform.runLater(() -> {
                if (isStaleLoad(loadToken, dir)) return;// 目录已切换，丢弃结果
                emptyTipLabel.setVisible(false);
                emptyTipLabel.setManaged(false);
                allFiles.addAll(visibleFiles);
                fileSizeCache.putAll(finalSizeCache);
                cachedImageCount = finalImageCount;
                cachedTotalSize = finalTotalSize;
                vBoxFactory.setHoverEffectsEnabled(enableHoverEffects);

                // 只处理初始批次的文件
                int nonImageCount = Math.min(nonImageFiles.size(), INITIAL_BATCH_SIZE);
                List<File> initialNonImage = nonImageFiles.subList(0, nonImageCount);
                int remaining = INITIAL_BATCH_SIZE - nonImageCount;
                int imageCount2 = Math.min(imageFiles.size(), remaining);
                List<File> initialImage = imageFiles.subList(0, imageCount2);

                // 存储待加载的文件
                pendingNonImageFiles = new ArrayList<>(nonImageFiles.subList(nonImageCount, nonImageFiles.size()));
                pendingImageFiles = new ArrayList<>(imageFiles.subList(imageCount2, imageFiles.size()));

                // 为初始批次创建构建任务（直接添加到 FlowPane，不使用渲染管道）
                nextInsertIndex = 0;
                for (File file : initialNonImage) {
                    pendingBuildTasks.addLast(() -> createVBoxAsync(file, vBox -> {
                        if (isStaleLoad(loadToken, dir)) return;
                        addVBoxToFlowPaneAt(vBox, nextInsertIndex);
                    }));
                    nextInsertIndex++;
                }

                for (File imageFile : initialImage) {
                    int expectedIndex = nextInsertIndex++;
                    pendingBuildTasks.addLast(() -> createImageVBoxAsync(imageFile, vBox -> {
                        if (isStaleLoad(loadToken, dir)) return;
                        addVBoxToFlowPaneAt(vBox, expectedIndex);
                    }));
                }

                renderStrategy.startBuildPipeline(pendingBuildTasks, BUILD_BATCH_SIZE, () -> {
                    isLoadingMore = false;
                });
                updateTipLabel();
            });
        });
    }

    /**
     * 将构建好的 VBox 卡片插入到 FlowPane 的指定位置，并同步布局。
     * 用于异步加载的图片卡片，保证即使回调乱序也能插入到正确的位置。
     */
    private void addVBoxToFlowPaneAt(VBox vBox, int expectedIndex) {
        int insertAt = Math.min(expectedIndex, imageFlowPane.getChildren().size());
        imageFlowPane.getChildren().add(insertAt, vBox);
        javafx.scene.layout.FlowPane.setMargin(vBox, new javafx.geometry.Insets(5));
        Platform.runLater(this::syncAnchorPaneHeight);
    }

    /**
     * 根据 FlowPane 实际内容计算并同步 AnchorPane 高度。
     * 遍历所有子节点，取最大的（layoutY + prefHeight + margin）作为内容总高度，
     * 加上 FlowPane 的底部 padding 后设置为 AnchorPane 的首选高度。
     */
    private void syncAnchorPaneHeight() {
        double contentH = 0;
        for (javafx.scene.Node child : imageFlowPane.getChildren()) {
            double childEnd = child.getLayoutY() + child.prefHeight(-1);
            javafx.geometry.Insets margin = javafx.scene.layout.FlowPane.getMargin(child);
            if (margin != null) childEnd += margin.getBottom();
            if (childEnd > contentH) contentH = childEnd;
        }
        javafx.geometry.Insets padding = imageFlowPane.getPadding();
        double totalH = contentH + (padding != null ? padding.getBottom() : 0);
        double viewportH = imageScrollPane.getViewportBounds() != null
                ? imageScrollPane.getViewportBounds().getHeight() : 0;
        imageAnchorPane.setPrefHeight(Math.max(totalH, viewportH));
    }

    /** 判断是否还有未加载的文件（用于决定是否触发懒加载） */
    private boolean hasMoreFiles() {
        return !pendingNonImageFiles.isEmpty() || !pendingImageFiles.isEmpty();
    }

    /**
     * 滚动触底时加载更多文件（懒加载）。
     * <p>从 pendingNonImageFiles 和 pendingImageFiles 中取出最多 LOAD_MORE_BATCH_SIZE(60) 个文件，
     * 创建构建任务并交给 RenderStrategy 分批执行。</p>
     */
    private void loadMoreFiles() {
        if (!hasMoreFiles()) return;
        isLoadingMore = true;
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            isLoadingMore = false;
            return;
        }
        long loadToken = activeLoadToken;

        // 取出下一批文件
        List<File> batchNonImage = new ArrayList<>();
        List<File> batchImage = new ArrayList<>();
        int remaining = LOAD_MORE_BATCH_SIZE;

        while (remaining > 0 && !pendingNonImageFiles.isEmpty()) {
            batchNonImage.add(pendingNonImageFiles.remove(0));
            remaining--;
        }
        while (remaining > 0 && !pendingImageFiles.isEmpty()) {
            batchImage.add(pendingImageFiles.remove(0));
            remaining--;
        }

        if (batchNonImage.isEmpty() && batchImage.isEmpty()) {
            isLoadingMore = false;
            return;
        }

        for (File file : batchNonImage) {
            pendingBuildTasks.addLast(() -> createVBoxAsync(file, vBox -> {
                if (isStaleLoad(loadToken, currentDir)) return;
                addVBoxToFlowPaneAt(vBox, nextInsertIndex);
            }));
            nextInsertIndex++;
        }

        for (File imageFile : batchImage) {
            int expectedIndex = nextInsertIndex++;
            pendingBuildTasks.addLast(() -> createImageVBoxAsync(imageFile, vBox -> {
                if (isStaleLoad(loadToken, currentDir)) return;
                addVBoxToFlowPaneAt(vBox, expectedIndex);
            }));
        }

        renderStrategy.startBuildPipeline(pendingBuildTasks, BUILD_BATCH_SIZE, () -> {
            isLoadingMore = false;
        });
    }

    /**
     * 判断当前加载是否已过期（用户在异步加载期间切换了目录）。
     * 若 loadToken 不匹配或当前目录已改变，则返回 true，调用方应丢弃本次结果。
     */
    private boolean isStaleLoad(long loadToken, File dir) {
        return loadToken != activeLoadToken || !Objects.equals(navigationService.getCurrentDirectory(), dir);
    }

    /** 异步创建非图片文件的 VBox 卡片（委托给 VBoxFactory） */
    private void createVBoxAsync(File file, Consumer<VBox> callback) {
        vBoxFactory.createVBoxAsync(
                file, callback, selectedVBoxes, vBoxToFile,
                NORMAL_STYLE, SELECTED_STYLE,
                this::updateTipLabel,
                () -> navigateToDirectory(file, true)
        );
    }

    /** 异步创建图片文件的 VBox 卡片（含缩略图加载，委托给 VBoxFactory） */
    private void createImageVBoxAsync(File file, Consumer<VBox> callback) {
        vBoxFactory.createImageVBoxAsync(
                file, callback,
                imageService.getExecutor(), THUMB_SIZE,
                NORMAL_STYLE, SELECTED_STYLE,
                selectedVBoxes, vBoxToFile,
                this::updateTipLabel,
                () -> openSlideShowForImage(file),
                imageService,
                () -> vBoxFactory.buildContextMenu(selectedVBoxes.size(), this::deleteSelected, this::copySelected, this::renameSelected, this::pasteFiles)
        );
    }

    /**
     * 初始化 FlowPane 的快捷入口。
     * 在尚未进入任何目录时，显示系统磁盘根目录和"我的图片"目录的快捷方式卡片，
     * 方便用户快速跳转。
     */
    private void initFlowPaneHint() {
        File[] roots = directoryService.getSystemRoots();
        for (File root : roots) {
            createShortcutVBox(root, root.getAbsolutePath());
        }
        File picturesDir = directoryService.getPicturesDirectory();
        if (picturesDir != null) {
            createShortcutVBox(picturesDir, "我的图片");
        }
    }

    /**
     * 创建快捷方式 VBox 卡片。
     * 点击后导航到目标目录，并同步更新左侧目录树的选中状态。
     * @param targetDir   目标目录
     * @param displayName 显示名称（如"本地磁盘 (C:)"或"我的图片"）
     */
    private void createShortcutVBox(File targetDir, String displayName) {
        vBoxFactory.createShortcutVBox(
                displayName, THUMB_SIZE, NORMAL_STYLE, SELECTED_STYLE,
                selectedVBoxes, imageFlowPane, this::updateTipLabel,
                () -> {
                    // 将目标目录的父级链路压入后退栈（由远到近），使后退按钮可逐级返回
                    List<File> ancestors = new ArrayList<>();
                    File parent = targetDir.getParentFile();
                    while (parent != null) {
                        ancestors.add(parent);
                        parent = parent.getParentFile();
                    }
                    for (int i = ancestors.size() - 1; i >= 0; i--) {
                        navigationService.pushBackStack(ancestors.get(i));
                    }
                    navigateToDirectory(targetDir, false);
                    if (dirTreeView.getRoot() != null) {
                        directoryTreeService.expandAndSelectInTree(targetDir.getAbsolutePath());
                    }
                }
        );
    }

    /** FXML 绑定：工具栏"幻灯片播放"按钮，从当前目录的第一张图片开始播放 */
    @FXML
    private void openSlideShow() {
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            showAlert(Alert.AlertType.WARNING, "未选择目录", "请选择包含图片的文件夹");
            return;
        }
        List<String> imagePaths = imageService.getImagePaths(currentDir);
        if (imagePaths.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "未选择目录", "请选择包含图片的文件夹");
            return;
        }
        showSlideShowWindow(imagePaths, 0);
    }

    /**
     * FXML 绑定：FlowPane 空白区域的鼠标点击事件。
     * 左键点击清空选中并关闭菜单；右键点击弹出空白区域上下文菜单。
     */
    @FXML
    private void clickBlank(javafx.scene.input.MouseEvent event) {
        if (event.getTarget() != imageFlowPane) return;
        if (event.getButton() == MouseButton.PRIMARY) {
            hideBlankContextMenu();
            clearSelection();
        } else if (event.getButton() == MouseButton.SECONDARY) {
            hideBlankContextMenu();
            blankContextMenu = vBoxFactory.buildContextMenu(0, this::deleteSelected, this::copySelected, this::renameSelected, this::pasteFiles);
            blankContextMenu.show(imageFlowPane, event.getScreenX(), event.getScreenY());
            event.consume();
        }
    }

    /** FXML 绑定：工具栏"后退"按钮，从历史栈中弹出上一个目录并导航 */
    @FXML
    public void onBack() {
        File prev = navigationService.goBack();
        if (prev != null) {
            pathField.setText(prev.getAbsolutePath());
            loadImagesToFlowPane(prev);
            directoryTreeService.expandAndSelectInTree(prev.getAbsolutePath());
        }
    }

    /** FXML 绑定：工具栏"前进"按钮，从前进栈中弹出下一个目录并导航 */
    @FXML
    public void onForward() {
        File next = navigationService.goForward();
        if (next != null) {
            pathField.setText(next.getAbsolutePath());
            loadImagesToFlowPane(next);
            directoryTreeService.expandAndSelectInTree(next.getAbsolutePath());
        }
    }

    /**
     * 更新底部状态栏标签。
     * 显示内容：目录名 | 图片数量 | 总大小 [| 选中数量] [| 选中图片总大小]
     */
    private void updateTipLabel() {
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            tipLabel.setText("Welcome to Image Manager");
            return;
        }
        String sizeStr = formatSize(cachedTotalSize);
        String selectedStr = selectedVBoxes.isEmpty() ? "" : " | 选中: " + selectedVBoxes.size();
        long selectedImageSize = selectedVBoxes.stream()
                .map(vBoxToFile::get)
                .filter(Objects::nonNull)
                .filter(imageService::isImageFile)
                .mapToLong(f -> fileSizeCache.getOrDefault(f, 0L))
                .sum();
        String selectedSizeStr = (selectedVBoxes.isEmpty() || selectedImageSize == 0) ? "" : " | 选中图片总大小: " + formatSize(selectedImageSize);
        tipLabel.setText("目录: " + currentDir.getName() + " | 图片数量: " + cachedImageCount + " | 总大小: " + sizeStr + selectedStr + selectedSizeStr);
    }

    /**
     * 将字节数格式化为人类可读的文件大小字符串。
     * 例如：1048576 → "1.0 MB"
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * 创建并显示幻灯片播放窗口。
     * 加载 slideShow.fxml，设置图片列表和起始索引，以模态窗口方式打开。
     * @param imagePaths 图片文件路径列表
     * @param startIndex 起始播放的图片索引
     */
    private void showSlideShowWindow(List<String> imagePaths, int startIndex) {
        try {
            URL fxmlUrl = getClass().getResource("/slideShow.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent slideRoot = loader.load();
            SlideShowController slideController = loader.getController();
            slideController.setImagePaths(imagePaths);
            if (startIndex > 0) slideController.setCurrentIndex(startIndex);
            Stage slideStage = new Stage();
            slideStage.setTitle("幻灯片播放");
            slideStage.setScene(new javafx.scene.Scene(slideRoot, 1000, 700));
            slideStage.setMinWidth(420);
            slideStage.setMinHeight(340);
            slideStage.initOwner(dirTreeView.getScene().getWindow());
            slideStage.centerOnScreen();
            slideStage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "打开幻灯片失败", e.getMessage());
        }
    }

    /**
     * 双击图片卡片时打开幻灯片，并定位到该图片。
     * 获取当前目录所有图片路径列表，找到目标图片的索引后打开幻灯片窗口。
     */
    private void openSlideShowForImage(File imageFile) {
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            showAlert(Alert.AlertType.WARNING, "未找到图片", "当前目录中没有可播放的图片");
            return;
        }
        List<String> imagePaths = imageService.getImagePaths(currentDir);
        if (imagePaths.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "未找到图片", "当前目录中没有可播放的图片");
            return;
        }
        int index = imagePaths.indexOf(imageFile.getAbsolutePath());
        showSlideShowWindow(imagePaths, Math.max(index, 0));
    }

    /** 清空所有选中的卡片，恢复为普通样式，并更新状态栏 */
    private void clearSelection() {
        selectedVBoxes.forEach(v -> v.setStyle(NORMAL_STYLE));
        selectedVBoxes.clear();
        updateTipLabel();
    }

    /**
     * 删除选中的文件。
     * 弹出确认对话框，确认后调用 FileOperationService 执行删除，
     * 然后从 FlowPane 中移除对应的 VBox 卡片，并重新计算目录统计信息。
     */
    private void deleteSelected() {
        if (selectedVBoxes.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "确认删除选中的文件？", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                Set<File> filesToDelete = new HashSet<>();
                for (VBox vBox : selectedVBoxes) {
                    File file = vBoxToFile.get(vBox);
                    if (file != null) filesToDelete.add(file);
                }
                List<File> deleted = fileOperationService.deleteFiles(filesToDelete);
                Platform.runLater(() -> {
                    Set<VBox> toRemove = new HashSet<>();
                    for (Map.Entry<VBox, File> entry : vBoxToFile.entrySet()) {
                        if (deleted.contains(entry.getValue())) {
                            toRemove.add(entry.getKey());
                        }
                    }
                    imageFlowPane.getChildren().removeAll(toRemove);
                    selectedVBoxes.removeAll(toRemove);
                    for (VBox v : toRemove) vBoxToFile.remove(v);
                    allFiles.removeAll(deleted);
                    recalculateDirectoryStats();
                    updateTipLabel();
                });
            }
        });
    }

    /**
     * 复制选中的文件到系统剪贴板。
     * 从 selectedVBoxes 中收集对应的 File 对象，委托给 FileOperationService 处理。
     */
    public void copySelected() {
        List<File> files = new ArrayList<>();
        for (VBox vBox : selectedVBoxes) {
            File file = vBoxToFile.get(vBox);
            if (file != null) files.add(file);
        }
        fileOperationService.copyToClipboard(files);
    }

    /**
     * 重命名单个选中的文件。
     * 仅支持单选重命名，多选时弹出提示。弹出输入对话框，用户输入新名称后
     * 自动保留原扩展名，调用 FileOperationService 执行重命名并更新 UI。
     */
    private void renameSelected() {
        if (selectedVBoxes.isEmpty()) return;
        if (selectedVBoxes.size() > 1) {
            showAlert(Alert.AlertType.INFORMATION, "提示", "多选时不支持重命名");
            return;
        }
        VBox vBox = selectedVBoxes.iterator().next();
        File file = vBoxToFile.get(vBox);
        if (file == null) return;
        TextInputDialog dialog = new TextInputDialog(file.getName());
        dialog.setTitle("重命名");
        dialog.setHeaderText("输入新文件名");
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                String ext = "";
                int dotIndex = file.getName().lastIndexOf('.');
                if (dotIndex > 0) ext = file.getName().substring(dotIndex);
                String newNameWithExt = newName.contains(".") ? newName : newName + ext;
                if (fileOperationService.renameFile(file, newNameWithExt)) {
                    File newFile = new File(file.getParent(), newNameWithExt);
                    vBoxToFile.put(vBox, newFile);
                    ((Label) vBox.getChildren().get(1)).setText(VBoxFactory.truncateFileName(newNameWithExt));
                    allFiles.set(allFiles.indexOf(file), newFile);
                    recalculateDirectoryStats();
                } else {
                    showAlert(Alert.AlertType.ERROR, "重命名失败", "无法重命名文件");
                }
            }
        });
    }

    /**
     * 从系统剪贴板粘贴文件到当前目录。
     * 委托给 FileOperationService 执行粘贴操作，然后重新加载当前目录以显示新文件。
     */
    private void pasteFiles() {
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            showAlert(Alert.AlertType.WARNING, "无法粘贴", "请先选择一个目录再粘贴文件");
            return;
        }
        fileOperationService.pasteFiles(currentDir);
        loadImagesToFlowPane(currentDir);
    }

    /**
     * 重新计算目录统计信息（图片数量和总大小）。
     * 在文件删除或重命名后调用，确保状态栏显示的数据与实际一致。
     */
    private void recalculateDirectoryStats() {
        long[] stats = fileOperationService.calculateDirStats(allFiles);
        cachedImageCount = stats[0];
        cachedTotalSize = stats[1];
    }
    
    /** 显示提示弹窗（委托给 AlterUtil，自动设置 owner 为当前窗口） */
    private void showAlert(Alert.AlertType type, String title, String content) {
        AlterUtil.showAlert(type, title, content, dirTreeView.getScene().getWindow());
    }

    /**
     * 应用关闭时的资源清理方法。
     * 停止渲染管道、关闭目录树线程池、清空图片缓存。
     */
    public void shutdown() {
        renderStrategy.stopAll();
        if (directoryTreeService != null) directoryTreeService.shutdown();
        imageService.clearCache();
    }
}