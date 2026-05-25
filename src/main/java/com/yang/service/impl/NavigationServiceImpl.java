package com.yang.service.impl;

import com.yang.repository.FileRepository;
import com.yang.service.NavigationService;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.Stack;

/** 导航服务业务逻辑层实现 */
@Service

public class NavigationServiceImpl implements NavigationService {

    private final FileRepository fileRepository;

    private File currentDirectory;
    /** 后退历史栈 */
    private final Stack<File> backStack = new Stack<>();
    /** 前进历史栈 */
    private final Stack<File> forwardStack = new Stack<>();

    public NavigationServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /** 导航到指定目录，将当前目录压入后退栈并清空前进栈 */
    @Override
    public boolean navigateTo(File dir) {
        if (dir == null || !fileRepository.isDirectory(dir)) return false;
        if (dir.equals(currentDirectory)) return false;
        if (currentDirectory != null) {
            backStack.push(currentDirectory);
        }
        forwardStack.clear();
        currentDirectory = dir;
        return true;
    }

    /** 返回上级目录 */
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

    /** 后退：当前目录压入前进栈，从后退栈弹出恢复 */
    @Override
    public File goBack() {
        if (backStack.isEmpty()) return null;
        forwardStack.push(currentDirectory);
        currentDirectory = backStack.pop();
        return currentDirectory;
    }

    /** 前进：当前目录压入后退栈，从前进栈弹出恢复 */
    @Override
    public File goForward() {
        if (forwardStack.isEmpty()) return null;
        backStack.push(currentDirectory);
        currentDirectory = forwardStack.pop();
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

    /** 是否有后退历史 */
    @Override
    public boolean hasBackHistory() {
        return !backStack.isEmpty();
    }

    /** 是否有前进历史 */
    @Override
    public boolean hasForwardHistory() {
        return !forwardStack.isEmpty();
    }

    /** 清空所有导航历史 */
    @Override
    public void clearHistory() {
        backStack.clear();
        forwardStack.clear();
    }

    /** 将目录压入后退栈（用于快捷方式跳转时保存历史） */
    @Override
    public void pushBackStack(File dir) {
        if (dir != null && fileRepository.isDirectory(dir)) {
            backStack.push(dir);
        }
    }

    /** 当前是否在驱动器根目录（如 C:\、D:\） */
    @Override
    public boolean isAtDriveRoot() {
        return currentDirectory != null && currentDirectory.getParentFile() == null;
    }

    /** 解析路径字符串为有效的目录 File，无效则返回 null */
    @Override
    public File resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File file = new File(path);
        if (fileRepository.exists(file) && fileRepository.isDirectory(file)) {
            return file;
        }
        return null;
    }
}
