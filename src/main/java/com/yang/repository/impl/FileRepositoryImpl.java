package com.yang.repository.impl;

import com.yang.repository.FileRepository;
import org.springframework.stereotype.Repository;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件系统数据访问层实现
 * 封装所有文件系统读写操作
 */
@Repository
public class FileRepositoryImpl implements FileRepository {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp"
    );

    @Override
    public List<File> listVisibleFiles(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        return Arrays.stream(files)
                .filter(this::isVisibleFile)
                .sorted((f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteFile(File file) {
        return file.delete();
    }

    @Override
    public boolean renameFile(File file, String newNameWithExt) {
        File newFile = new File(file.getParent(), newNameWithExt);
        return file.renameTo(newFile);
    }

    @Override
    public File copyFileTo(File src, File destDir) {
        try {
            File dest = new File(destDir, src.getName());
            if (dest.exists()) {
                String name = src.getName();
                int dotIndex = name.lastIndexOf('.');
                String base = dotIndex > 0 ? name.substring(0, dotIndex) : name;
                String ext = dotIndex > 0 ? name.substring(dotIndex) : "";
                int count = 1;
                while (dest.exists()) {
                    dest = new File(destDir, base + "(" + count + ")" + ext);
                    count++;
                }
            }
            Files.copy(src.toPath(), dest.toPath());
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isImageFile(File file) {
        if (!file.isFile()) return false;
        String lower = file.getName().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    @Override
    public boolean isVisibleFile(File file) {
        return !file.isHidden() && !file.getName().startsWith(".");
    }

    @Override
    public String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public List<String> getImagePaths(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        List<String> paths = Arrays.stream(files)
                .filter(File::isFile)
                .filter(this::isImageFile)
                .map(File::getAbsolutePath)
                .sorted()
                .collect(Collectors.toList());
        return paths;
    }
}
