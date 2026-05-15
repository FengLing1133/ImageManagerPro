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

/** 主窗口控制器：管理目录树与文件卡片面板的联动、分批懒加载、文件交互操作 */
@Component
public class MainController {

    @FXML private TreeView<String> dirTreeView;
    @FXML private ScrollPane imageScrollPane;
    @FXML private AnchorPane imageAnchorPane;
    @FXML private FlowPane imageFlowPane;
    @FXML private Label tipLabel;
    @FXML private TextField pathField;
    @FXML private Label emptyTipLabel;

    private final NavigationService navigationService;
    private final FileOperationService fileOperationService;
    private final ImageService imageService;
    private final DirectoryService directoryService;
    private final FileRepository fileRepository;
    private final RenderStrategy renderStrategy;

    private final VBoxFactory vBoxFactory = new VBoxFactory();
    private DirectoryTreeService directoryTreeService;

    // 选中模型
    private final Set<VBox> selectedVBoxes = new HashSet<>();
    private final Map<VBox, File> vBoxToFile = new HashMap<>();

    // 文件统计缓存
    private final List<File> allFiles = new ArrayList<>();
    private final Map<File, Long> fileSizeCache = new HashMap<>();
    private long cachedImageCount = 0;
    private long cachedTotalSize = 0;

    // 分批懒加载
    /** loadToken 单调递增，异步回调比对不匹配则丢弃，防止目录切换竞态 */
    private long activeLoadToken = 0;
    private static final int INITIAL_BATCH_SIZE = 120;
    private static final int LOAD_MORE_BATCH_SIZE = 60;
    private List<File> pendingImageFiles = new ArrayList<>();
    private List<File> pendingNonImageFiles = new ArrayList<>();
    private volatile boolean isLoadingMore = false;
    private static final int BUILD_BATCH_SIZE = 50;
    private final Deque<Runnable> pendingBuildTasks = new ArrayDeque<>();
    private int nextInsertIndex = 0;

    // 卡片样式
    private static final String NORMAL_STYLE = "-fx-alignment: center; -fx-border-color: #E5E7EB; -fx-border-width: 1.5px; -fx-background-color: #FFFFFF; -fx-background-radius: 8; -fx-border-radius: 8;";
    private static final String SELECTED_STYLE = "-fx-alignment: center; -fx-border-color: #06B6D4; -fx-border-width: 2px; -fx-background-color: #ECFEFF; -fx-background-radius: 8; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(6, 182, 212, 0.15), 10, 0, 0, 2);";

    private ContextMenu blankContextMenu = null;
    private static final int THUMB_SIZE = 120;
    /** 文件数超过此值时禁用卡片悬停动画，避免卡顿 */
    private static final int HOVER_EFFECT_THRESHOLD = 500;

    /** 构造器注入 */
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

    /** FXML 初始化 */
    @FXML
    public void initialize() {
        directoryTreeService = new DirectoryTreeService(dirTreeView);
        directoryTreeService.initDirectoryTree();
        setupDirTreeCellFactory();
        setupDirTreeSelectionListener();
        setupPathFieldListener();
        initFlowPaneHint();
        
        imageScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            imageFlowPane.setPrefWidth(newVal.getWidth());
            imageAnchorPane.setPrefWidth(newVal.getWidth());
            Platform.runLater(this::syncAnchorPaneHeight);
        });

        imageScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            double vvalue = newVal.doubleValue();
            if (vvalue > 0.89 && !isLoadingMore && hasMoreFiles()) {
                loadMoreFiles();
            }
        });

        imageAnchorPane.setOnContextMenuRequested(event -> {
            if (event.getTarget() == imageAnchorPane) {
                clearSelection();
                hideBlankContextMenu();
                blankContextMenu = vBoxFactory.buildContextMenu(0, this::deleteSelected, this::copySelected, this::renameSelected, this::pasteFiles);
                blankContextMenu.show(imageAnchorPane, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });

        imageFlowPane.setOnMousePressed(event -> hideBlankContextMenu());
        imageAnchorPane.setOnMousePressed(event -> { if (event.getButton() == MouseButton.PRIMARY) hideBlankContextMenu(); });
    }

    /** 隐藏空白区域右键菜单 */
    private void hideBlankContextMenu() {
        if (blankContextMenu != null && blankContextMenu.isShowing()) {
            blankContextMenu.hide();
        }
    }

    /** 配置目录树单元格工厂：按节点类型显示图标，单击切换展开/折叠 */
    private void setupDirTreeCellFactory() {
        dirTreeView.setCellFactory(tv -> {
            javafx.scene.control.TreeCell<String> cell = new javafx.scene.control.TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
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

    /** 目录树选中监听：选中节点时导航到对应目录 */
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
     * 导航到指定目录
     * @param syncTreeSelection 是否同步目录树选中状态
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

    /** 路径输入框回车跳转：有效路径则导航，无效则短暂标红提示 */
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

    /** 加载目录文件到 FlowPane：后台扫描 → 首批 120 个立即加载 → 剩余排队懒加载 */
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
                    if (isStaleLoad(loadToken, dir)) return;
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
            final boolean enableHoverEffects = visibleFiles.size() <= HOVER_EFFECT_THRESHOLD;

            Platform.runLater(() -> {
                if (isStaleLoad(loadToken, dir)) return;
                emptyTipLabel.setVisible(false);
                emptyTipLabel.setManaged(false);
                allFiles.addAll(visibleFiles);
                fileSizeCache.putAll(finalSizeCache);
                cachedImageCount = finalImageCount;
                cachedTotalSize = finalTotalSize;
                vBoxFactory.setHoverEffectsEnabled(enableHoverEffects);

                int nonImageCount = Math.min(nonImageFiles.size(), INITIAL_BATCH_SIZE);
                List<File> initialNonImage = nonImageFiles.subList(0, nonImageCount);
                int remaining = INITIAL_BATCH_SIZE - nonImageCount;
                int imageCount2 = Math.min(imageFiles.size(), remaining);
                List<File> initialImage = imageFiles.subList(0, imageCount2);

                pendingNonImageFiles = new ArrayList<>(nonImageFiles.subList(nonImageCount, nonImageFiles.size()));
                pendingImageFiles = new ArrayList<>(imageFiles.subList(imageCount2, imageFiles.size()));

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

    /** 将 VBox 插入 FlowPane 指定位置，保证异步回调乱序时仍按正确顺序排列 */
    private void addVBoxToFlowPaneAt(VBox vBox, int expectedIndex) {
        int insertAt = Math.min(expectedIndex, imageFlowPane.getChildren().size());
        imageFlowPane.getChildren().add(insertAt, vBox);
        javafx.scene.layout.FlowPane.setMargin(vBox, new javafx.geometry.Insets(5));
        Platform.runLater(this::syncAnchorPaneHeight);
    }

    /** 根据 FlowPane 内容计算 AnchorPane 高度，保证滚动视口正确 */
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

    /** 是否还有未加载的文件 */
    private boolean hasMoreFiles() {
        return !pendingNonImageFiles.isEmpty() || !pendingImageFiles.isEmpty();
    }

    /** 滚动触底时从待加载队列取最多 60 个文件继续加载 */
    private void loadMoreFiles() {
        if (!hasMoreFiles()) return;
        isLoadingMore = true;
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            isLoadingMore = false;
            return;
        }
        long loadToken = activeLoadToken;

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

    /** 判断异步加载是否已过期（用户已切换目录），过期则丢弃结果 */
    private boolean isStaleLoad(long loadToken, File dir) {
        return loadToken != activeLoadToken || !Objects.equals(navigationService.getCurrentDirectory(), dir);
    }

    /** 异步创建非图片文件卡片 */
    private void createVBoxAsync(File file, Consumer<VBox> callback) {
        vBoxFactory.createVBoxAsync(
                file, callback, selectedVBoxes, vBoxToFile,
                NORMAL_STYLE, SELECTED_STYLE,
                this::updateTipLabel,
                () -> navigateToDirectory(file, true)
        );
    }

    /** 异步创建图片文件卡片（含缩略图） */
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

    /** 初始化快捷入口：显示磁盘根目录和"我的图片"快捷方式 */
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

    /** 创建快捷方式卡片，点击导航到目标目录并同步目录树 */
    private void createShortcutVBox(File targetDir, String displayName) {
        vBoxFactory.createShortcutVBox(
                displayName, THUMB_SIZE, NORMAL_STYLE, SELECTED_STYLE,
                selectedVBoxes, imageFlowPane, this::updateTipLabel,
                () -> {
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

    /** 工具栏"幻灯片播放"按钮 */
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

    /** FlowPane 空白区域点击：左键清空选中，右键弹出菜单 */
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

    /** 后退按钮 */
    @FXML
    public void onBack() {
        File prev = navigationService.goBack();
        if (prev != null) {
            pathField.setText(prev.getAbsolutePath());
            loadImagesToFlowPane(prev);
            directoryTreeService.expandAndSelectInTree(prev.getAbsolutePath());
        }
    }

    /** 前进按钮 */
    @FXML
    public void onForward() {
        File next = navigationService.goForward();
        if (next != null) {
            pathField.setText(next.getAbsolutePath());
            loadImagesToFlowPane(next);
            directoryTreeService.expandAndSelectInTree(next.getAbsolutePath());
        }
    }

    /** 更新底部状态栏 */
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

    /** 字节数格式化为可读大小，如 1048576 → "1.0 MB" */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /** 打开幻灯片播放窗口 */
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

    /** 双击图片卡片时打开幻灯片并定位到该图片 */
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
        int index = indexOfPath(imagePaths, imageFile);
        System.out.println("[DEBUG] 双击图片: " + imageFile.getAbsolutePath());
        System.out.println("[DEBUG] indexOf结果: " + index + ", imagePaths数量: " + imagePaths.size());
        if (index < 0) {
            System.out.println("[DEBUG] indexOf失败! 首个路径: " + imagePaths.get(0));
            System.out.println("[DEBUG] 匹配尝试: " + imagePaths.indexOf(imageFile.getAbsolutePath()));
        }
        showSlideShowWindow(imagePaths, Math.max(index, 0));
    }

    /** 在路径列表中查找文件索引，先尝试精确匹配，失败则按文件名匹配 */
    private int indexOfPath(List<String> imagePaths, File imageFile) {
        // 先尝试精确路径匹配
        int idx = imagePaths.indexOf(imageFile.getAbsolutePath());
        if (idx >= 0) return idx;
        // 精确匹配失败，按文件名匹配
        String targetName = imageFile.getName();
        for (int i = 0; i < imagePaths.size(); i++) {
            if (imagePaths.get(i).endsWith(File.separator + targetName)) {
                return i;
            }
        }
        return -1;
    }

    /** 清空选中状态 */
    private void clearSelection() {
        selectedVBoxes.forEach(v -> v.setStyle(NORMAL_STYLE));
        selectedVBoxes.clear();
        updateTipLabel();
    }

    /** 删除选中文件（弹确认框） */
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

    /** 复制选中文件到剪贴板 */
    public void copySelected() {
        List<File> files = new ArrayList<>();
        for (VBox vBox : selectedVBoxes) {
            File file = vBoxToFile.get(vBox);
            if (file != null) files.add(file);
        }
        fileOperationService.copyToClipboard(files);
    }

    /** 重命名单个选中文件（仅单选，自动保留扩展名） */
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

    /** 从剪贴板粘贴文件到当前目录 */
    private void pasteFiles() {
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            showAlert(Alert.AlertType.WARNING, "无法粘贴", "请先选择一个目录再粘贴文件");
            return;
        }
        fileOperationService.pasteFiles(currentDir);
        loadImagesToFlowPane(currentDir);
    }

    /** 重新计算目录统计（删除/重命名后调用） */
    private void recalculateDirectoryStats() {
        long[] stats = fileOperationService.calculateDirStats(allFiles);
        cachedImageCount = stats[0];
        cachedTotalSize = stats[1];
    }
    
    /** 显示提示弹窗 */
    private void showAlert(Alert.AlertType type, String title, String content) {
        AlterUtil.showAlert(type, title, content, dirTreeView.getScene().getWindow());
    }

    /** 关闭时清理资源 */
    public void shutdown() {
        renderStrategy.stopAll();
        if (directoryTreeService != null) directoryTreeService.shutdown();
        imageService.clearCache();
    }
}