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
    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();

    @Override
    public boolean navigateTo(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        if (dir.equals(currentDirectory)) return false;
        if (currentDirectory != null) {
            backStack.push(currentDirectory);
        }
        forwardStack.clear();
        currentDirectory = dir;
        return true;
    }

    @Override
    public File goUp() {
        if (currentDirectory == null) return null;
        File parent = currentDirectory.getParentFile();
        if (parent != null) {
            backStack.push(currentDirectory);
            forwardStack.clear();
            currentDirectory = parent;
        }
        return parent;
    }

    @Override
    public File goBack() {
        if (backStack.isEmpty()) return null;
        forwardStack.push(currentDirectory);
        currentDirectory = backStack.pop();
        return currentDirectory;
    }

    @Override
    public File goForward() {
        if (forwardStack.isEmpty()) return null;
        backStack.push(currentDirectory);
        currentDirectory = forwardStack.pop();
        return currentDirectory;
    }

    @Override
    public File undoNavigation() {
        if (backStack.isEmpty()) return null;
        currentDirectory = backStack.pop();
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
        return !backStack.isEmpty();
    }

    @Override
    public boolean hasBackHistory() {
        return !backStack.isEmpty();
    }

    @Override
    public boolean hasForwardHistory() {
        return !forwardStack.isEmpty();
    }

    @Override
    public void clearHistory() {
        backStack.clear();
        forwardStack.clear();
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
