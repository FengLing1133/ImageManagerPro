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

    private final VBoxFactory vBoxFactory = new VBoxFactory();
    private DirectoryTreeService directoryTreeService; //目录树服务，管理 TreeView的异步加载、路径展开和状态追踪

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
    private static final int LOAD_MORE_BATCH_SIZE = 90;
    private List<File> pendingImageFiles = new ArrayList<>();
    private List<File> pendingNonImageFiles = new ArrayList<>();
    private volatile boolean isLoadingMore = false;
    private static final int BUILD_BATCH_SIZE = 30;
    private final Deque<Runnable> pendingBuildTasks = new ArrayDeque<>();
    private int nextInsertIndex = 0;

    private ContextMenu blankContextMenu = null;
    private static final int THUMB_SIZE = 120;
    private static final int HOVER_EFFECT_THRESHOLD = 500;

    private final NavigationService navigationService; //导航服务业务逻辑层 封装目录导航、历史栈、路径解析等业务逻辑
    private final FileOperationService fileOperationService; //文件操作业务逻辑层 处理文件的增删改查业务逻辑
    private final ImageService imageService; //图片业务逻辑层 封装图片加载、缓存管理、缩略图生成等业务逻辑
    private final DirectoryService directoryService; //目录服务业务逻辑层 封装目录树构建、子目录加载等业务逻辑
    private final FileRepository fileRepository; //文件系统数据访问层 封装所有文件系统读写操作，隔离 I0 细节
    private final RenderStrategy renderStrategy; //渲染策略接口 决定如何将构建好的 UI 卡片渲染到FLowPane

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

    @FXML
    public void initialize() {
        directoryTreeService = new DirectoryTreeService(dirTreeView); //目录树服务，管理 TreeView的异步加载、路径展开和状态追踪
        directoryTreeService.initDirectoryTree(); //初始化"我的电脑"根节点，为每个磁盘盘符创建子节点并注册展开监听
        setupDirTreeCellFactory(); //配置目录树单元格工厂:按节点类型显示图标，单击切换展开/折叠
        setupDirTreeSelectionListener(); //目录树选中监听:选中节点时导航到对应目录
        setupPathFieldListener(); //路径输入框回车跳转:有效路径则导航，无效则短暂标红提示
        initFlowPaneHint(); //初始化快捷入口:显示磁盘根目录和"我的图片"快捷方式
        
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
                    super.updateItem(item, empty);//更新单元格内部的 item 属性和 empty 状态
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
                pathField.getStyleClass().add("path-field-error");
                pathField.setText("路径无效");
                PauseTransition pause = new PauseTransition(Duration.seconds(1));
                pause.setOnFinished(e -> {
                    File cur = navigationService.getCurrentDirectory();
                    if (cur != null) pathField.setText(cur.getAbsolutePath());
                    pathField.getStyleClass().remove("path-field-error");
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
        imageService.submitImageLoadTask(() -> {
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
                renderStrategy.startBuildPipeline(pendingBuildTasks, BUILD_BATCH_SIZE, () -> isLoadingMore = false);
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

    /** 滚动触底时从待加载队列取最多 90 个文件继续加载 */
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
            batchNonImage.add(pendingNonImageFiles.removeFirst());
            remaining--;
        }
        while (remaining > 0 && !pendingImageFiles.isEmpty()) {
            batchImage.add(pendingImageFiles.removeFirst());
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
        renderStrategy.startBuildPipeline(pendingBuildTasks, BUILD_BATCH_SIZE, () -> isLoadingMore = false);
    }

    /** 判断异步加载是否已过期（用户已切换目录），过期则丢弃结果 */
    private boolean isStaleLoad(long loadToken, File dir) {
        return loadToken != activeLoadToken || !Objects.equals(navigationService.getCurrentDirectory(), dir);
    }

    /** 异步创建非图片文件卡片 */
    private void createVBoxAsync(File file, Consumer<VBox> callback) {
        vBoxFactory.createVBoxAsync(
                file, callback, selectedVBoxes, vBoxToFile,
                this::updateTipLabel,
                () -> navigateToDirectory(file, true)
        );
    }

    /** 异步创建图片文件卡片（含缩略图） */
    private void createImageVBoxAsync(File file, Consumer<VBox> callback) {
        vBoxFactory.createImageVBoxAsync(
                file, callback, THUMB_SIZE,
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
                displayName, THUMB_SIZE, selectedVBoxes, imageFlowPane, this::updateTipLabel,
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
        } else if (navigationService.isAtDriveRoot()) {
            showQuickEntry();
        }
    }

    /** 向上按钮：返回上级目录，根目录时显示快捷入口 */
    @FXML
    public void onUp() {
        File parent = navigationService.goUp();
        if (parent != null) {
            pathField.setText(parent.getAbsolutePath());
            loadImagesToFlowPane(parent);
            directoryTreeService.expandAndSelectInTree(parent.getAbsolutePath());
        } else if (navigationService.isAtDriveRoot()) {
            showQuickEntry();
        }
    }

    /** 显示快捷入口视图（磁盘卡片和"我的图片"） */
    private void showQuickEntry() {
        navigationService.setCurrentDirectory(null);
        pathField.clear();
        imageFlowPane.getChildren().clear();
        selectedVBoxes.clear();
        vBoxToFile.clear();
        emptyTipLabel.setVisible(false);
        emptyTipLabel.setManaged(false);
        initFlowPaneHint();
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
            slideController.setImagePaths(imagePaths, startIndex);
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
        int index = imagePaths.indexOf(imageFile.getAbsolutePath());
        showSlideShowWindow(imagePaths, Math.max(index, 0));
    }

    /** 清空选中状态 */
    private void clearSelection() {
        selectedVBoxes.forEach(v -> {
            v.getStyleClass().remove("card-selected");
            if (!v.getStyleClass().contains("card-normal")) v.getStyleClass().add("card-normal");
        });
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

    /** 重命名选中文件，多选时按显示顺序添加数字后缀 (1)(2)(3)... */
    private void renameSelected() {
        if (selectedVBoxes.isEmpty()) return;

        // 按 allFiles 中的显示顺序排序选中的 VBox
        List<VBox> orderedSelected = selectedVBoxes.stream()
                .sorted(Comparator.comparingInt(vb -> {
                    File f = vBoxToFile.get(vb);
                    return f != null ? allFiles.indexOf(f) : Integer.MAX_VALUE;
                }))
                .toList();

        if (orderedSelected.size() == 1) {
            // 单选：保持原有行为
            VBox vBox = orderedSelected.getFirst();
            File file = vBoxToFile.get(vBox);
            if (file == null) return;
            TextInputDialog dialog = new TextInputDialog(file.getName());
            dialog.setTitle("重命名");
            dialog.setHeaderText("输入新文件名");
            dialog.showAndWait().ifPresent(newName -> {
                if (!newName.trim().isEmpty()) {
                    String ext = getExtension(file.getName());
                    String newNameWithExt = newName.contains(".") ? newName : newName + ext;
                    if (fileOperationService.renameFile(file, newNameWithExt)) {
                        updateAfterRename(vBox, file, newNameWithExt);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "重命名失败", "无法重命名文件");
                    }
                }
            });
        } else {
            // 多选：批量重命名，按顺序添加数字后缀
            File firstFile = vBoxToFile.get(orderedSelected.getFirst());
            if (firstFile == null) return;
            TextInputDialog dialog = new TextInputDialog(firstFile.getName());
            dialog.setTitle("批量重命名");
            dialog.setHeaderText("输入新文件名（将自动添加数字后缀）");
            dialog.setContentText("将重命名 " + orderedSelected.size() + " 个文件");
            dialog.showAndWait().ifPresent(baseName -> {
                if (baseName.trim().isEmpty()) return;
                String baseNameNoExt = baseName.contains(".") ? baseName.substring(0, baseName.lastIndexOf('.')) : baseName;
                int successCount = 0;
                for (int i = 0; i < orderedSelected.size(); i++) {
                    VBox vBox = orderedSelected.get(i);
                    File file = vBoxToFile.get(vBox);
                    if (file == null) continue;
                    String fileExt = getExtension(file.getName());
                    String newNameWithExt = baseNameNoExt + "(" + (i + 1) + ")" + fileExt;
                    if (fileOperationService.renameFile(file, newNameWithExt)) {
                        updateAfterRename(vBox, file, newNameWithExt);
                        successCount++;
                    }
                }
                if (successCount < orderedSelected.size()) {
                    showAlert(Alert.AlertType.WARNING, "批量重命名完成",
                            "成功 " + successCount + " 个，失败 " + (orderedSelected.size() - successCount) + " 个");
                }
            });
        }
    }

    /** 获取文件扩展名（含点号），无扩展名返回空字符串 */
    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }

    /** 重命名成功后更新 UI 和数据 */
    private void updateAfterRename(VBox vBox, File oldFile, String newNameWithExt) {
        File newFile = new File(oldFile.getParent(), newNameWithExt);
        vBoxToFile.put(vBox, newFile);
        ((Label) vBox.getChildren().get(1)).setText(VBoxFactory.truncateFileName(newNameWithExt));
        int idx = allFiles.indexOf(oldFile);
        if (idx >= 0) allFiles.set(idx, newFile);
        recalculateDirectoryStats();
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
        imageService.shutdown();
    }
}