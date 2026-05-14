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

@Component
public class MainController {

    @FXML
    private TreeView<String> dirTreeView;
    @FXML
    private FlowPane imageFlowPane;
    @FXML
    private ScrollPane imageScrollPane;
    @FXML
    private Label tipLabel;
    @FXML
    private TextField pathField;
    @FXML
    private AnchorPane imageAnchorPane;
    @FXML
    private Label emptyTipLabel;

    private final NavigationService navigationService;
    private final FileOperationService fileOperationService;
    private final ImageService imageService;
    private final DirectoryService directoryService;
    private final FileRepository fileRepository;
    private final RenderStrategy renderStrategy;
    private final VBoxFactory vBoxFactory = new VBoxFactory();

    private DirectoryTreeService directoryTreeService;
    private final Set<VBox> selectedVBoxes = new HashSet<>();
    private final Map<VBox, File> vBoxToFile = new HashMap<>();
    private final Map<File, Long> fileSizeCache = new HashMap<>();
    private final List<File> allFiles = new ArrayList<>();
    private long cachedImageCount = 0;
    private long cachedTotalSize = 0;
    private long activeLoadToken = 0;

    // 懒加载相关
    private static final int INITIAL_BATCH_SIZE = 120;
    private static final int LOAD_MORE_BATCH_SIZE = 60;
    private List<File> pendingImageFiles = new ArrayList<>();
    private List<File> pendingNonImageFiles = new ArrayList<>();
    private volatile boolean isLoadingMore = false;

    private static final String NORMAL_STYLE = "-fx-alignment: center; -fx-border-color: #d4dce8; -fx-border-width: 1.5px; -fx-background-color: #ffffff; -fx-background-radius: 14; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(56, 68, 84, 0.11), 10, 0, 0, 2);";
    private static final String SELECTED_STYLE = "-fx-alignment: center; -fx-border-color: #5a98ea; -fx-border-width: 2px; -fx-background-color: #eaf2ff; -fx-background-radius: 14; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(90, 152, 234, 0.24), 12, 0, 0, 2);";
    private ContextMenu blankContextMenu = null;
    private static final int THUMB_SIZE = 120;
    private static final int HOVER_EFFECT_THRESHOLD = 500;
    private static final int BUILD_BATCH_SIZE = 50;

    private final Deque<Runnable> pendingBuildTasks = new ArrayDeque<>();

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
        directoryTreeService = new DirectoryTreeService(dirTreeView);
        setupDirTreeCellFactory();
        setupDirTreeSelectionListener();
        setupPathFieldListener();
        initFlowPaneHint();
        directoryTreeService.initDirectoryTree();

        // 视口尺寸变化时更新 FlowPane/AnchorPane 宽度，并同步 AnchorPane 高度
        imageScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            imageFlowPane.setPrefWidth(newVal.getWidth());
            imageAnchorPane.setPrefWidth(newVal.getWidth());
            if (imageFlowPane.getChildren().isEmpty()) {
                imageAnchorPane.setPrefHeight(Math.max(newVal.getHeight(), 0));
            } else {
                // 宽度变化会导致 FlowPane 重新换行，高度改变，需延迟重新计算
                Platform.runLater(this::syncAnchorPaneHeight);
            }
        });

        // FlowPane 宽度变化时也需要同步高度（换行导致高度改变）
        imageFlowPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (!imageFlowPane.getChildren().isEmpty()) {
                Platform.runLater(this::syncAnchorPaneHeight);
            }
        });

        // AnchorPane 高度跟随 FlowPane 内容高度
        imageFlowPane.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                syncAnchorPaneHeight();
            }
        });

        // 子节点变化时同步高度
        imageFlowPane.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) c -> {
            if (imageFlowPane.getChildren().isEmpty()) {
                double viewportH = imageScrollPane.getViewportBounds() != null
                        ? imageScrollPane.getViewportBounds().getHeight() : 0;
                imageAnchorPane.setPrefHeight(Math.max(viewportH, 0));
            } else {
                Platform.runLater(this::syncAnchorPaneHeight);
            }
        });

        // 滚动到底部时加载更多图片
        imageScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            double vvalue = newVal.doubleValue();
            if (vvalue > 0.89 && !isLoadingMore && hasMoreFiles()) {
                loadMoreFiles();
            }
        });

        // 监听内容区域大小变化，当内容不足一屏时自动加载更多
        imageFlowPane.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (newHeight.doubleValue() > 0 && !isLoadingMore && hasMoreFiles()) {
                double viewportHeight = imageScrollPane.getViewportBounds().getHeight();
                if (viewportHeight > 0 && newHeight.doubleValue() <= viewportHeight * 1.5) {
                    loadMoreFiles();
                }
            }
        });

        imageFlowPane.setOnContextMenuRequested(event -> {
            if (event.getTarget() != imageFlowPane) return;
            clearSelection();
            hideBlankContextMenu();
            blankContextMenu = vBoxFactory.buildContextMenu(0, this::deleteSelected, this::copySelected, this::renameSelected, this::pasteFiles);
            blankContextMenu.show(imageFlowPane, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        imageScrollPane.setOnContextMenuRequested(event -> {
            if (event.getTarget() == imageScrollPane || event.getTarget() == imageScrollPane.getContent()) {
                hideBlankContextMenu();
                blankContextMenu = vBoxFactory.buildContextMenu(0, this::deleteSelected, this::copySelected, this::renameSelected, this::pasteFiles);
                blankContextMenu.show(imageScrollPane, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });

        imageAnchorPane.setOnContextMenuRequested(event -> {
            if (event.getTarget() == imageAnchorPane) {
                hideBlankContextMenu();
                blankContextMenu = vBoxFactory.buildContextMenu(0, this::deleteSelected, this::copySelected, this::renameSelected, this::pasteFiles);
                blankContextMenu.show(imageAnchorPane, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });

        imageFlowPane.setOnMousePressed(event -> hideBlankContextMenu());
        imageAnchorPane.setOnMousePressed(event -> { if (event.getButton() == MouseButton.PRIMARY) hideBlankContextMenu(); });
        imageScrollPane.setOnMousePressed(event -> { if (event.getButton() == MouseButton.PRIMARY) hideBlankContextMenu(); });
    }

    private void hideBlankContextMenu() {
        if (blankContextMenu != null && blankContextMenu.isShowing()) {
            blankContextMenu.hide();
        }
    }

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

    private void setupDirTreeSelectionListener() {
        dirTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                String fullPath = directoryTreeService.getFullPath(newItem);
                File selectedDir = new File(fullPath);
                navigateToDirectory(selectedDir, false);
            }
        });
    }

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

    private void loadImagesToFlowPane(File dir, Runnable onComplete) {
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
                for (File file : initialNonImage) {
                    pendingBuildTasks.addLast(() -> createVBoxAsync(file, vBox -> {
                        if (isStaleLoad(loadToken, dir)) return;
                        addVBoxToFlowPane(vBox);
                    }));
                }

                for (File imageFile : initialImage) {
                    pendingBuildTasks.addLast(() -> createImageVBoxAsync(imageFile, vBox -> {
                        if (isStaleLoad(loadToken, dir)) return;
                        addVBoxToFlowPane(vBox);
                    }));
                }

                renderStrategy.startBuildPipeline(pendingBuildTasks, BUILD_BATCH_SIZE, () -> {
                    isLoadingMore = false;
                    if (onComplete != null) onComplete.run();
                });
                updateTipLabel();
            });
        });
    }

    private void loadImagesToFlowPane(File dir) {
        loadImagesToFlowPane(dir, null);
    }

    // 直接添加 VBox 到 FlowPane
    private void addVBoxToFlowPane(VBox vBox) {
        imageFlowPane.getChildren().add(vBox);
        javafx.scene.layout.FlowPane.setMargin(vBox, new javafx.geometry.Insets(5));
        syncAnchorPaneHeight();
        imageScrollPane.requestLayout();
    }

    // 根据 FlowPane 实际内容计算并同步 AnchorPane 高度
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
        if (totalH > 0) {
            imageAnchorPane.setPrefHeight(totalH);
        }
    }

    private boolean hasMoreFiles() {
        return !pendingNonImageFiles.isEmpty() || !pendingImageFiles.isEmpty();
    }

    // 滚动到底部时加载更多文件
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
                addVBoxToFlowPane(vBox);
            }));
        }

        for (File imageFile : batchImage) {
            pendingBuildTasks.addLast(() -> createImageVBoxAsync(imageFile, vBox -> {
                if (isStaleLoad(loadToken, currentDir)) return;
                addVBoxToFlowPane(vBox);
            }));
        }

        renderStrategy.startBuildPipeline(pendingBuildTasks, BUILD_BATCH_SIZE, () -> {
            isLoadingMore = false;
        });
    }

    private boolean isStaleLoad(long loadToken, File dir) {
        return loadToken != activeLoadToken || !Objects.equals(navigationService.getCurrentDirectory(), dir);
    }

    private void createVBoxAsync(File file, Consumer<VBox> callback) {
        vBoxFactory.createVBoxAsync(
                file, callback, selectedVBoxes, vBoxToFile,
                NORMAL_STYLE, SELECTED_STYLE,
                this::updateTipLabel,
                () -> navigateToDirectory(file, true)
        );
    }

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

    private void createShortcutVBox(File targetDir, String displayName) {
        vBoxFactory.createShortcutVBox(
                displayName, THUMB_SIZE, NORMAL_STYLE, SELECTED_STYLE,
                selectedVBoxes, imageFlowPane, this::updateTipLabel,
                () -> {
                    navigateToDirectory(targetDir, false);
                    if (dirTreeView.getRoot() != null) {
                        directoryTreeService.expandAndSelectInTree(targetDir.getAbsolutePath());
                    }
                }
        );
    }

    @FXML
    private void openSlideShow() {
        File currentDir = navigationService.getCurrentDirectory();
        if (currentDir == null) {
            showAlert(Alert.AlertType.WARNING, "未选择目录", "请先在左侧选择包含图片的文件夹");
            return;
        }
        List<String> imagePaths = imageService.getImagePaths(currentDir);
        if (imagePaths.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "未选择目录", "请先在左侧选择包含图片的文件夹");
            return;
        }
        showSlideShowWindow(imagePaths, 0);
    }

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

    @FXML
    public void onBack() {
        File prev = navigationService.goBack();
        if (prev != null) {
            pathField.setText(prev.getAbsolutePath());
            loadImagesToFlowPane(prev);
            directoryTreeService.expandAndSelectInTree(prev.getAbsolutePath());
        }
    }

    @FXML
    public void onForward() {
        File next = navigationService.goForward();
        if (next != null) {
            pathField.setText(next.getAbsolutePath());
            loadImagesToFlowPane(next);
            directoryTreeService.expandAndSelectInTree(next.getAbsolutePath());
        }
    }

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

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void clearSelection() {
        selectedVBoxes.forEach(v -> v.setStyle(NORMAL_STYLE));
        selectedVBoxes.clear();
        updateTipLabel();
    }

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

    @FXML
    public void copySelected() {
        List<File> files = new ArrayList<>();
        for (VBox vBox : selectedVBoxes) {
            File file = vBoxToFile.get(vBox);
            if (file != null) files.add(file);
        }
        fileOperationService.copyToClipboard(files);
    }

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

    private void pasteFiles() {
        File currentDir = navigationService.getCurrentDirectory();
        fileOperationService.pasteFiles(currentDir);
        loadImagesToFlowPane(currentDir);
    }

    private void recalculateDirectoryStats() {
        long[] stats = fileOperationService.calculateDirStats(allFiles);
        cachedImageCount = stats[0];
        cachedTotalSize = stats[1];
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        AlterUtil.showAlert(type, title, content, dirTreeView.getScene().getWindow());
    }

    public void shutdown() {
        renderStrategy.stopAll();
        if (directoryTreeService != null) directoryTreeService.shutdown();
        imageService.clearCache();
    }
}
