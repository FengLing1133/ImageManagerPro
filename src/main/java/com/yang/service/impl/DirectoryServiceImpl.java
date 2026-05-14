package com.yang.service.impl;

import com.yang.repository.FileRepository;
import com.yang.service.DirectoryService;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 目录服务，负责子目录列表获取、系统根目录和用户图片目录 */
@Service
public class DirectoryServiceImpl implements DirectoryService {

    /** 需要过滤的系统目录名称 */
    private static final Set<String> SYSTEM_DIRS = Set.of(
            "System Volume Information",
            "$Recycle.Bin",
            "Windows",
            "Program Files"
    );

    private final FileRepository fileRepository;

    public DirectoryServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /** 获取子目录列表，过滤系统目录，按名称排序 */
    @Override
    public List<File> listChildDirectories(File parent) {
        File[] children = parent.listFiles(File::isDirectory);
        if (children == null) return Collections.emptyList();
        return Arrays.stream(children)
                .filter(file -> !isSystemDirectory(file))
                .sorted((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()))
                .collect(Collectors.toList());
    }

    /** 获取系统根目录（盘符）列表 */
    @Override
    public File[] getSystemRoots() {
        File[] roots = File.listRoots();
        return roots != null ? roots : new File[0];
    }

    /** 获取用户图片目录（~/Pictures），不存在则返回 null */
    @Override
    public File getPicturesDirectory() {
        String userHome = System.getProperty("user.home");
        File picturesDir = new File(userHome, "Pictures");
        if (picturesDir.exists() && picturesDir.isDirectory()) {
            return picturesDir;
        }
        return null;
    }

    /** 系统目录或隐藏目录均视为系统目录 */
    @Override
    public boolean isSystemDirectory(File dir) {
        return SYSTEM_DIRS.contains(dir.getName()) || dir.isHidden();
    }
}
