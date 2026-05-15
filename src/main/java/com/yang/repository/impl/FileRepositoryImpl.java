package com.yang.repository.impl;

import com.yang.repository.FileRepository;
import org.springframework.stereotype.Repository;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/** 文件系统数据访问层，封装文件列出、删除、重命名、复制等操作 */
@Repository
public class FileRepositoryImpl implements FileRepository {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp"
    );

    /** 列出目录下可见文件，目录优先，同类型按名称排序 */
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

    /** 重命名文件，newNameWithExt 为包含扩展名的新名称 */
    @Override
    public boolean renameFile(File file, String newNameWithExt) {
        File newFile = new File(file.getParent(), newNameWithExt);
        return file.renameTo(newFile);
    }

    /** 复制文件到目标目录，重名时自动追加数字后缀 (1)(2)... */
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

    /** 根据扩展名判断是否为图片文件 */
    @Override
    public boolean isImageFile(File file) {
        if (!file.isFile()) return false;
        String lower = file.getName().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /** 非隐藏且文件名不以点开头 */
    @Override
    public boolean isVisibleFile(File file) {
        return !file.isHidden() && !file.getName().startsWith(".");
    }

    /** 将字节大小格式化为可读字符串（KB/MB/GB...） */
    @Override
    public String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /** 获取目录下所有图片文件的绝对路径（已排序） */
    @Override
    public List<String> getImagePaths(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        return Arrays.stream(files)
                .filter(File::isFile)
                .filter(this::isImageFile)
                .sorted((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()))
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
    }
}
