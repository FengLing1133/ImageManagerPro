package com.yang.repository.impl;

import com.yang.repository.FileRepository;
import org.springframework.stereotype.Repository;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件系统数据访问层实现
 * 封装所有文件系统读写操作，包括文件的列出、删除、重命名、复制等基础操作
 * 以及图片文件的识别和路径获取功能
 */
@Repository
public class FileRepositoryImpl implements FileRepository {

    /**
     * 支持的图片文件扩展名集合
     * 用于判断文件是否为图片类型
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp"
    );

    /**
     * 列出目录下所有可见文件和子目录
     * 过滤掉隐藏文件和以点开头的文件，并按以下规则排序：
     * 1. 目录排在文件前面
     * 2. 同类型按文件名不区分大小写排序
     * @param directory 要扫描的目录
     * @return 排序后的可见文件列表，如果目录为空则返回空列表
     */
    @Override
    public List<File> listVisibleFiles(File directory) {
        // 获取目录下所有文件，如果目录为空则返回空列表
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();

        // 使用流处理：过滤隐藏文件 -> 排序 -> 收集为列表
        return Arrays.stream(files)
                .filter(this::isVisibleFile)  // 过滤掉隐藏文件
                .sorted((f1, f2) -> {
                    // 目录优先排序
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    // 同类型文件按名称不区分大小写排序
                    return f1.getName().compareToIgnoreCase(f2.getName());
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除指定文件
     * @param file 要删除的文件
     * @return 删除成功返回 true，失败返回 false
     */
    @Override
    public boolean deleteFile(File file) {
        return file.delete();
    }

    /**
     * 重命名文件
     * 将文件重命名为指定的新名称（包含扩展名）
     * @param file           要重命名的文件
     * @param newNameWithExt 新的文件名（包含扩展名，例如 "photo.jpg"）
     * @return 重命名成功返回 true，失败返回 false
     */
    @Override
    public boolean renameFile(File file, String newNameWithExt) {
        // 构建新文件路径（同一父目录下）
        File newFile = new File(file.getParent(), newNameWithExt);
        return file.renameTo(newFile);
    }

    /**
     * 复制文件到指定目录
     * 如果目标目录已存在同名文件，会自动在文件名后追加数字后缀
     * 例如：photo.jpg -> photo(1).jpg -> photo(2).jpg
     * @param src     源文件
     * @param destDir 目标目录
     * @return 复制成功返回目标文件对象，失败返回 null
     */
    @Override
    public File copyFileTo(File src, File destDir) {
        try {
            // 构建目标文件路径
            File dest = new File(destDir, src.getName());

            // 处理重名文件：自动追加数字后缀
            if (dest.exists()) {
                String name = src.getName();
                int dotIndex = name.lastIndexOf('.');
                // 分离文件名和扩展名
                String base = dotIndex > 0 ? name.substring(0, dotIndex) : name;
                String ext = dotIndex > 0 ? name.substring(dotIndex) : "";
                int count = 1;
                // 循环查找可用的文件名
                while (dest.exists()) {
                    dest = new File(destDir, base + "(" + count + ")" + ext);
                    count++;
                }
            }

            // 执行文件复制
            Files.copy(src.toPath(), dest.toPath());
            return dest;
        } catch (Exception e) {
            // 复制失败返回 null
            return null;
        }
    }

    /**
     * 判断文件是否为图片文件
     * 通过检查文件扩展名来判断，支持 jpg、jpeg、png、gif、bmp 格式
     * @param file 要检查的文件
     * @return 是图片文件返回 true，否则返回 false
     */
    @Override
    public boolean isImageFile(File file) {
        // 先判断是否为文件（排除目录）
        if (!file.isFile()) return false;
        // 将文件名转为小写后检查扩展名
        String lower = file.getName().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /**
     * 判断文件是否为可见文件
     * 可见文件需要同时满足：
     * 1. 不是隐藏文件（系统属性）
     * 2. 文件名不以点开头（Unix 隐藏文件约定）
     * @param file 要检查的文件
     * @return 是可见文件返回 true，否则返回 false
     */
    @Override
    public boolean isVisibleFile(File file) {
        return !file.isHidden() && !file.getName().startsWith(".");
    }

    /**
     * 格式化文件大小为人类可读的字符串
     * 自动选择合适的单位（B、KB、MB、GB、TB、PB、EB）
     * @param bytes 文件大小（字节）
     * @return 格式化后的字符串，例如 "1.5 MB"、"2.3 GB"
     */
    @Override
    public String formatSize(long bytes) {
        // 小于 1KB 直接显示字节
        if (bytes < 1024) return bytes + " B";
        // 计算合适的单位等级（1024 的幂次）
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        // 获取单位前缀（K、M、G、T、P、E）
        String pre = "KMGTPE".charAt(exp - 1) + "";
        // 格式化为 "数值 单位B" 的形式
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * 获取目录下所有图片文件的绝对路径
     * 用于幻灯片播放功能，获取需要展示的图片列表
     * @param directory 要扫描的目录
     * @return 图片文件绝对路径列表（已排序），如果目录为空则返回空列表
     */
    @Override
    public List<String> getImagePaths(File directory) {
        // 获取目录下所有文件
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();

        // 使用流处理：过滤文件 -> 过滤图片 -> 提取路径 -> 排序 -> 收集
        List<String> paths = Arrays.stream(files)
                .filter(File::isFile)           // 只保留文件（排除目录）
                .filter(this::isImageFile)      // 只保留图片文件
                .map(File::getAbsolutePath)     // 提取绝对路径
                .sorted()                       // 按路径排序
                .collect(Collectors.toList());
        return paths;
    }
}
