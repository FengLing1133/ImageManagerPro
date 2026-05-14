package com.yang.service;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.util.Duration;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 目录树服务，管理 TreeView 的异步加载、路径展开和状态追踪 */
public class DirectoryTreeService {
    public DirectoryTreeService(TreeView<String> dirTreeView) {
        this.dirTreeView = dirTreeView;
    }

    private final TreeView<String> dirTreeView;
    /** 节点加载状态：unloaded → loading → loaded */
    private final Map<TreeItem<String>, String> treeItemStatus = new ConcurrentHashMap<>();
    private static final String STATUS_UNLOADED = "unloaded";
    private static final String STATUS_LOADING = "loading";
    private static final String STATUS_LOADED = "loaded";
    /** 2线程守护线程池，用于异步扫描目录 */
    private final ExecutorService dirExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("Directory Loader");
        return thread;
    });

    /** 初始化"我的电脑"根节点，为每个磁盘盘符创建子节点并注册展开监听 */
    public void initDirectoryTree() {
        TreeItem<String> computerRoot = new TreeItem<>("我的电脑");
        computerRoot.setExpanded(true);
        dirTreeView.setRoot(computerRoot);
        dirTreeView.setShowRoot(true);
        File[] roots = File.listRoots();
        if (roots == null) roots = new File[0];
        for (File root : roots) {
            TreeItem<String> driveItem = new TreeItem<>(root.getAbsolutePath());
            driveItem.setExpanded(false);
            treeItemStatus.put(driveItem, STATUS_UNLOADED);
            computerRoot.getChildren().add(driveItem);
            // 盘符展开时触发异步加载
            driveItem.expandedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal && STATUS_UNLOADED.equals(treeItemStatus.get(driveItem))) {
                    loadChildrenAsync(driveItem, root, 1);
                }
            });
        }
    }

    /** 异步加载子目录，过滤系统目录和隐藏文件，限制递归深度为5层 */
    private void loadChildrenAsync(TreeItem<String> parentItem, File parentFile, int depth) {
        String status = treeItemStatus.getOrDefault(parentItem, STATUS_UNLOADED);
        if (STATUS_LOADING.equals(status) || STATUS_LOADED.equals(status)) {
            return;
        }
        treeItemStatus.put(parentItem, STATUS_LOADING);
        if (depth > 5) {
            treeItemStatus.put(parentItem, STATUS_LOADED);
            return;
        }
        if (!parentItem.isExpanded()) {
            parentItem.setExpanded(true);
        }
        dirExecutor.submit(() -> {
            File[] childFiles = parentFile.listFiles(File::isDirectory);
            if (childFiles == null) {
                Platform.runLater(() -> {
                    TreeItem<String> emptyItem = new TreeItem<>("无访问权限");
                    parentItem.getChildren().add(emptyItem);
                });
                treeItemStatus.put(parentItem, STATUS_LOADED);
                return;
            }
            // 过滤系统目录和隐藏文件，按名称排序
            List<File> filteredFiles = Arrays.stream(childFiles)
                    .filter(file -> {
                        String name = file.getName();
                        return !name.equals("System Volume Information")
                                && !name.equals("$Recycle.Bin")
                                && !name.equals("Windows")
                                && !name.equals("Program Files")
                                && !file.isHidden();
                    })
                    .sorted((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()))
                    .toList();
            Platform.runLater(() -> {
                if (filteredFiles.isEmpty()) {
                    TreeItem<String> emptyItem = new TreeItem<>("无子目录");
                    parentItem.getChildren().add(emptyItem);
                } else {
                    for (File childFile : filteredFiles) {
                        TreeItem<String> childItem = new TreeItem<>(childFile.getName());
                        treeItemStatus.put(childItem, STATUS_UNLOADED);
                        childItem.expandedProperty().addListener((obs, oldVal, newVal) -> {
                            if (newVal && STATUS_UNLOADED.equals(treeItemStatus.get(childItem))) {
                                loadChildrenAsync(childItem, childFile, depth + 1);
                            }
                        });
                        parentItem.getChildren().add(childItem);
                    }
                }
                treeItemStatus.put(parentItem, STATUS_LOADED);
            });
        });
    }

    //递归拼接TreeItem的完整路径
    public String getFullPath(TreeItem<String> item) {
        if (item.getParent() == null || item.getParent().getValue().equals("我的电脑")) {
            return item.getValue(); // 根节点或盘符
        }
        String parentPath = getFullPath(item.getParent());
        String fullPath = parentPath + File.separator + item.getValue();
        return fullPath.replace("\\\\", "\\");
    }

    //展开并选中目录树中的指定路径
    public void expandAndSelectInTree(String targetPath) {
        TreeItem<String> root = dirTreeView.getRoot();
        if (root == null) {
            return;
        }
        Platform.runLater(() -> expandPathStepByStep(root, targetPath, 0)); // 进入递归展开
    }

    //逐级展开路径（使用 PauseTransition 替代 Thread.sleep，避免阻塞 UI 线程）
    private void expandPathStepByStep(TreeItem<String> currentItem, String targetPath, int depth) {
        String currentPath = getFullPath(currentItem);

        if (depth > 15) return; // 防止死循环

        if (targetPath.equals(currentPath)) {
            currentItem.setExpanded(true);
            dirTreeView.getSelectionModel().select(currentItem);
            return;
        }

        if (currentItem.getValue().equals("我的电脑")) { // 根节点特殊处理
            String driveLetter = targetPath.substring(0, 3);
            TreeItem<String> driveItem = findChildByName(currentItem, driveLetter);
            if (driveItem == null) return;

            if (!driveItem.isExpanded()) {
                driveItem.setExpanded(true);
            }
            // 非阻塞等待后继续展开
            PauseTransition pause = new PauseTransition(Duration.millis(300));
            pause.setOnFinished(e -> expandPathStepByStep(driveItem, targetPath, depth + 1));
            pause.play();
            return;
        }

        if (!targetPath.startsWith(currentPath)) return; // 路径不匹配

        String separator = File.separator.equals("\\") ? "\\\\" : File.separator;
        String remaining = targetPath.substring(currentPath.length());
        String[] parts = Arrays.stream(remaining.split(separator))
                .filter(s -> !s.isEmpty()).toArray(String[]::new);

        if (parts.length == 0) return;

        String nextName = parts[0];

        if (currentItem.getChildren().isEmpty()) { // 子节点未加载，非阻塞重试
            retryExpand(currentItem, targetPath, depth, 0);
            return;
        }

        TreeItem<String> nextChild = findChildByName(currentItem, nextName);
        if (nextChild == null) return;

        if (!nextChild.isExpanded()) { // 递归展开
            nextChild.setExpanded(true);
            PauseTransition pause = new PauseTransition(Duration.millis(200));
            pause.setOnFinished(e -> expandPathStepByStep(nextChild, targetPath, depth + 1));
            pause.play();
        } else {
            expandPathStepByStep(nextChild, targetPath, depth + 1);
        }
    }

    //非阻塞重试展开，避免无限循环
    private void retryExpand(TreeItem<String> item, String targetPath, int depth, int retryCount) {
        if (retryCount > 10) return;
        PauseTransition retryPause = new PauseTransition(Duration.millis(200));
        retryPause.setOnFinished(e -> {
            if (item.getChildren().isEmpty()) {
                retryExpand(item, targetPath, depth, retryCount + 1); // 继续重试
            } else {
                expandPathStepByStep(item, targetPath, depth); // 子节点已加载，继续展开
            }
        });
        retryPause.play();
    }

    //根据名称查找子节点
    private TreeItem<String> findChildByName(TreeItem<String> parent, String name) {
        for (TreeItem<String> child : parent.getChildren()) {
            if (child.getValue().equals(name)) { // 名称匹配
                return child;
            }
        }
        return null;
    }

    // 提供线程池关闭方法
    public void shutdown() {
        dirExecutor.shutdown();
    }
}
