package com.yang.repository;

import java.io.File;
import java.util.List;

/**
 * 文件系统数据访问层
 * 封装所有文件系统读写操作，隔离 IO 细节
 */
public interface FileRepository {

    /**
     * 列出目录下所有可见文件（过滤隐藏文件和系统文件）
     */
    List<File> listVisibleFiles(File directory);

    /**
     * 删除文件
     * @return 是否成功
     */
    boolean deleteFile(File file);

    /**
     * 重命名文件
     * @return 是否成功
     */
    boolean renameFile(File file, String newNameWithExt);

    /**
     * 复制文件到目标目录
     * @param src 源文件
     * @param destDir 目标目录
     * @return 复制后的文件，如果目标已存在则自动添加数字后缀
     */
    File copyFileTo(File src, File destDir);

    /**
     * 判断是否为图片文件（jpg/jpeg/png/gif/bmp）
     */
    boolean isImageFile(File file);

    /**
     * 判断是否为可见文件（非隐藏、非.开头）
     */
    boolean isVisibleFile(File file);

    /**
     * 格式化文件大小（B/KB/MB/GB）
     */
    String formatSize(long bytes);

    /**
     * 获取目录下所有图片文件路径（已排序）
     */
    List<String> getImagePaths(File directory);
}
