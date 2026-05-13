package com.yang.service;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * 文件操作业务逻辑层
 * 处理文件的增删改查业务逻辑
 */
public interface FileOperationService {

    /**
     * 批量删除文件
     * @param files 要删除的文件集合
     * @return 成功删除的文件列表
     */
    List<File> deleteFiles(Set<File> files);

    /**
     * 复制文件列表（记录到剪贴板）
     * @param files 要复制的文件
     */
    void copyToClipboard(List<File> files);

    /**
     * 获取剪贴板中的文件
     */
    List<File> getClipboardFiles();

    /**
     * 粘贴文件到目标目录
     * @param targetDir 目标目录
     * @return 粘贴后的文件列表
     */
    List<File> pasteFiles(File targetDir);

    /**
     * 重命名文件
     * @return 是否成功
     */
    boolean renameFile(File file, String newNameWithExt);

    /**
     * 计算目录统计信息（图片数量、总大小）
     * @return long[]{imageCount, totalSize}
     */
    long[] calculateDirStats(List<File> files);
}
