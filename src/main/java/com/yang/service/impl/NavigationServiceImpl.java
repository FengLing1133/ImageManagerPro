package com.yang.service.impl;

import com.yang.service.NavigationService;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.Stack;

/**
 * 导航服务业务逻辑层实现
 */
@Service
public class NavigationServiceImpl implements NavigationService {

    private File currentDirectory;
    private final Stack<File> historyStack = new Stack<>();

    @Override
    public boolean navigateTo(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        if (dir.equals(currentDirectory)) return false;
        if (currentDirectory != null) {
            historyStack.push(currentDirectory);
        }
        currentDirectory = dir;
        return true;
    }

    @Override
    public File goUp() {
        if (currentDirectory == null) return null;
        File parent = currentDirectory.getParentFile();
        if (parent != null) {
            historyStack.push(currentDirectory);
            currentDirectory = parent;
        }
        return parent;
    }

    @Override
    public File undoNavigation() {
        if (historyStack.isEmpty()) return null;
        currentDirectory = historyStack.pop();
        return currentDirectory;
    }

    @Override
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    @Override
    public void setCurrentDirectory(File dir) {
        this.currentDirectory = dir;
    }

    @Override
    public boolean hasHistory() {
        return !historyStack.isEmpty();
    }

    @Override
    public void clearHistory() {
        historyStack.clear();
    }

    @Override
    public File resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File file = new File(path);
        if (file.exists() && file.isDirectory()) {
            return file;
        }
        return null;
    }
}
