package com.yang.service.impl;

import com.yang.repository.FileRepository;
import com.yang.service.DirectoryService;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 目录服务，负责子目录列表获取、系统根目录和用户图片目录 */
@Service
public class DirectoryServiceImpl implements DirectoryService {

    /** 需要过滤的系统目录名称（业务逻辑层过滤） */
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
        return fileRepository.listChildDirectories(parent).stream()
                .filter(file -> !isSystemDirectory(file))
                .collect(Collectors.toList());
    }

    /** 获取系统根目录（盘符）列表 */
    @Override
    public File[] getSystemRoots() {
        return fileRepository.getSystemRoots();
    }

    /** 获取用户图片目录（~/Pictures），不存在则返回 null */
    @Override
    public File getPicturesDirectory() {
        return fileRepository.getPicturesDirectory();
    }

    /** 系统目录或隐藏目录均视为系统目录 */
    @Override
    public boolean isSystemDirectory(File dir) {
        return SYSTEM_DIRS.contains(dir.getName()) || fileRepository.isHidden(dir);
    }
}
