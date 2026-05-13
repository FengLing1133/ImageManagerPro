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

/**
 * 目录服务业务逻辑层实现
 */
@Service
public class DirectoryServiceImpl implements DirectoryService {

    private static final Set<String> SYSTEM_DIRS = Set.of(
            "System Volume Information", "$Recycle.Bin", "Windows", "Program Files"
    );

    private final FileRepository fileRepository;

    public DirectoryServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public List<File> listChildDirectories(File parent) {
        File[] children = parent.listFiles(File::isDirectory);
        if (children == null) return Collections.emptyList();
        return Arrays.stream(children)
                .filter(file -> !isSystemDirectory(file))
                .sorted((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public File[] getSystemRoots() {
        File[] roots = File.listRoots();
        return roots != null ? roots : new File[0];
    }

    @Override
    public File getPicturesDirectory() {
        String userHome = System.getProperty("user.home");
        File picturesDir = new File(userHome, "Pictures");
        if (picturesDir.exists() && picturesDir.isDirectory()) {
            return picturesDir;
        }
        return null;
    }

    @Override
    public boolean isSystemDirectory(File dir) {
        return SYSTEM_DIRS.contains(dir.getName()) || dir.isHidden();
    }
}
