package com.yang.service.impl;

import com.yang.repository.FileRepository;
import com.yang.service.FileOperationService;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 文件操作服务，负责批量删除、复制粘贴、重命名和目录统计 */
@Service
public class FileOperationServiceImpl implements FileOperationService {

    private final FileRepository fileRepository;

    /** 内存剪贴板，仅在当前应用生命周期内有效 */
    private final List<File> clipboardFiles = new ArrayList<>();

    public FileOperationServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /** 批量删除，返回成功删除的文件列表 */
    @Override
    public List<File> deleteFiles(Set<File> files) {
        List<File> deleted = new ArrayList<>();
        for (File file : files) {
            if (fileRepository.deleteFile(file)) {
                deleted.add(file);
            }
        }
        return deleted;
    }

    /** 将文件列表复制到剪贴板（替换原有内容） */
    @Override
    public void copyToClipboard(List<File> files) {
        clipboardFiles.clear();
        clipboardFiles.addAll(files);
    }

    /** 获取剪贴板内容的拷贝 */
    @Override
    public List<File> getClipboardFiles() {
        return new ArrayList<>(clipboardFiles);
    }

    /** 将剪贴板文件粘贴到目标目录，返回成功粘贴的新文件列表，不清空剪贴板 */
    @Override
    public List<File> pasteFiles(File targetDir) {
        List<File> pastedFiles = new ArrayList<>();
        if (clipboardFiles.isEmpty() || targetDir == null) return pastedFiles;
        for (File src : clipboardFiles) {
            File dest = fileRepository.copyFileTo(src, targetDir);
            if (dest != null) {
                pastedFiles.add(dest);
            }
        }
        return pastedFiles;
    }

    /** 重命名文件，newNameWithExt 需包含扩展名 */
    @Override
    public boolean renameFile(File file, String newNameWithExt) {
        return fileRepository.renameFile(file, newNameWithExt);
    }

    /** 统计文件列表的图片数量和总大小，返回 [图片数, 总字节] */
    @Override
    public long[] calculateDirStats(List<File> files) {
        long imageCount = 0;
        long totalSize = 0;
        for (File file : files) {
            if (file == null || !file.exists()) continue;
            if (file.isFile()) {
                totalSize += file.length();
                if (fileRepository.isImageFile(file)) {
                    imageCount++;
                }
            }
        }
        return new long[]{imageCount, totalSize};
    }
}
