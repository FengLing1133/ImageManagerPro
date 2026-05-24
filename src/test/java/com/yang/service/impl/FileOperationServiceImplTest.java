package com.yang.service.impl;

import com.yang.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileOperationServiceImplTest {

    @Mock
    private FileRepository fileRepository;

    private FileOperationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileOperationServiceImpl(fileRepository);
    }

    // === deleteFiles ===

    @Test
    @DisplayName("deleteFiles: 成功删除的文件加入结果列表")
    void deleteFiles_deletedFilesAddedToResultList() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);
        when(fileRepository.deleteFile(f1)).thenReturn(true);
        when(fileRepository.deleteFile(f2)).thenReturn(false);

        List<File> result = service.deleteFiles(Set.of(f1, f2));

        assertThat(result).containsExactly(f1);
    }

    @Test
    @DisplayName("deleteFiles: 全部删除成功")
    void deleteFiles_allDeletedSuccessfully() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);
        when(fileRepository.deleteFile(f1)).thenReturn(true);
        when(fileRepository.deleteFile(f2)).thenReturn(true);

        List<File> result = service.deleteFiles(Set.of(f1, f2));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("deleteFiles: 空集合返回空列表")
    void deleteFiles_emptySetReturnsEmptyList() {
        List<File> result = service.deleteFiles(Set.of());
        assertThat(result).isEmpty();
    }

    // === copyToClipboard / getClipboardFiles ===

    @Test
    @DisplayName("copyToClipboard: 存储文件列表")
    void copyToClipboard_storesFileList() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);

        service.copyToClipboard(List.of(f1, f2));

        assertThat(service.getClipboardFiles()).containsExactly(f1, f2);
    }

    @Test
    @DisplayName("getClipboardFiles: 返回副本不影响内部状态")
    void getClipboardFiles_returnsCopyWithoutAffectingInternalState() {
        File f1 = mock(File.class);
        service.copyToClipboard(List.of(f1));

        List<File> files = service.getClipboardFiles();
        files.clear();

        assertThat(service.getClipboardFiles()).hasSize(1);
    }

    @Test
    @DisplayName("copyToClipboard: 覆盖之前的剪贴板内容")
    void copyToClipboard_overwritesPreviousClipboardContent() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);

        service.copyToClipboard(List.of(f1));
        service.copyToClipboard(List.of(f2));

        assertThat(service.getClipboardFiles()).containsExactly(f2);
    }

    // === pasteFiles ===

    @Test
    @DisplayName("pasteFiles: 复制剪贴板文件到目标目录")
    void pasteFiles_copiesClipboardFilesToTargetDirectory() {
        File src = mock(File.class);
        File dest = mock(File.class);
        File targetDir = mock(File.class);

        service.copyToClipboard(List.of(src));
        when(fileRepository.copyFileTo(src, targetDir)).thenReturn(dest);

        List<File> result = service.pasteFiles(targetDir);

        assertThat(result).containsExactly(dest);
    }

    @Test
    @DisplayName("pasteFiles: 剪贴板为空返回空列表")
    void pasteFiles_emptyClipboardReturnsEmptyList() {
        File targetDir = mock(File.class);

        List<File> result = service.pasteFiles(targetDir);

        assertThat(result).isEmpty();
        verifyNoInteractions(fileRepository);
    }

    @Test
    @DisplayName("pasteFiles: 目标目录为null返回空列表")
    void pasteFiles_nullTargetDirectoryReturnsEmptyList() {
        File src = mock(File.class);
        service.copyToClipboard(List.of(src));

        List<File> result = service.pasteFiles(null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("pasteFiles: 复制失败的文件不加入结果")
    void pasteFiles_failedCopyNotAddedToResult() {
        File src = mock(File.class);
        File targetDir = mock(File.class);

        service.copyToClipboard(List.of(src));
        when(fileRepository.copyFileTo(src, targetDir)).thenReturn(null);

        List<File> result = service.pasteFiles(targetDir);

        assertThat(result).isEmpty();
    }

    // === renameFile ===

    @Test
    @DisplayName("renameFile: 委托给Repository")
    void renameFile_delegatesToRepository() {
        File file = mock(File.class);
        when(fileRepository.renameFile(file, "new.txt")).thenReturn(true);

        boolean result = service.renameFile(file, "new.txt");

        assertThat(result).isTrue();
        verify(fileRepository).renameFile(file, "new.txt");
    }

    @Test
    @DisplayName("renameFile: 重命名失败返回false")
    void renameFile_failureReturnsFalse() {
        File file = mock(File.class);
        when(fileRepository.renameFile(file, "new.txt")).thenReturn(false);

        boolean result = service.renameFile(file, "new.txt");

        assertThat(result).isFalse();
    }

    // === calculateDirStats ===

    @Test
    @DisplayName("calculateDirStats: 计算图片数量和总大小")
    void calculateDirStats_calculatesImageCountAndTotalSize() {
        File img = mock(File.class);
        File txt = mock(File.class);

        when(fileRepository.exists(img)).thenReturn(true);
        when(fileRepository.isFile(img)).thenReturn(true);
        when(fileRepository.getFileSize(img)).thenReturn(1024L);
        when(fileRepository.isImageFile(img)).thenReturn(true);

        when(fileRepository.exists(txt)).thenReturn(true);
        when(fileRepository.isFile(txt)).thenReturn(true);
        when(fileRepository.getFileSize(txt)).thenReturn(500L);
        when(fileRepository.isImageFile(txt)).thenReturn(false);

        long[] stats = service.calculateDirStats(List.of(img, txt));

        assertThat(stats[0]).isEqualTo(1); // 1 张图片
        assertThat(stats[1]).isEqualTo(1524L); // 总大小
    }

    @Test
    @DisplayName("calculateDirStats: null文件跳过")
    void calculateDirStats_skipsNullFiles() {
        File img = mock(File.class);
        when(fileRepository.exists(img)).thenReturn(true);
        when(fileRepository.isFile(img)).thenReturn(true);
        when(fileRepository.getFileSize(img)).thenReturn(100L);
        when(fileRepository.isImageFile(img)).thenReturn(true);

        long[] stats = service.calculateDirStats(Arrays.asList(null, img));

        assertThat(stats[0]).isEqualTo(1);
        assertThat(stats[1]).isEqualTo(100L);
    }

    @Test
    @DisplayName("calculateDirStats: 不存在的文件跳过")
    void calculateDirStats_skipsNonExistentFiles() {
        File file = mock(File.class);
        when(fileRepository.exists(file)).thenReturn(false);

        long[] stats = service.calculateDirStats(List.of(file));

        assertThat(stats[0]).isEqualTo(0);
        assertThat(stats[1]).isEqualTo(0L);
    }

    @Test
    @DisplayName("calculateDirStats: 空列表返回零")
    void calculateDirStats_emptyListReturnsZero() {
        long[] stats = service.calculateDirStats(List.of());

        assertThat(stats[0]).isEqualTo(0);
        assertThat(stats[1]).isEqualTo(0L);
    }
}
