package com.yang.service.impl;

import com.yang.repository.FileRepository;
import com.yang.service.FileOperationService;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 文件操作业务逻辑层实现
 */
@Service
public class FileOperationServiceImpl implements FileOperationService {

    private final FileRepository fileRepository;
    private final List<File> clipboardFiles = new ArrayList<>();

    public FileOperationServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

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

    @Override
    public void copyToClipboard(List<File> files) {
        clipboardFiles.clear();
        clipboardFiles.addAll(files);
    }

    @Override
    public List<File> getClipboardFiles() {
        return new ArrayList<>(clipboardFiles);
    }

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

    @Override
    public boolean renameFile(File file, String newNameWithExt) {
        return fileRepository.renameFile(file, newNameWithExt);
    }

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
