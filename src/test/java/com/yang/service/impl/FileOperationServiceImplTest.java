package com.yang.service.impl;

import com.yang.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
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
    void deleteFiles_成功删除的文件加入结果列表() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);
        when(fileRepository.deleteFile(f1)).thenReturn(true);
        when(fileRepository.deleteFile(f2)).thenReturn(false);

        List<File> result = service.deleteFiles(Set.of(f1, f2));

        assertThat(result).containsExactly(f1);
    }

    @Test
    void deleteFiles_全部删除成功() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);
        when(fileRepository.deleteFile(f1)).thenReturn(true);
        when(fileRepository.deleteFile(f2)).thenReturn(true);

        List<File> result = service.deleteFiles(Set.of(f1, f2));

        assertThat(result).hasSize(2);
    }

    @Test
    void deleteFiles_空集合返回空列表() {
        List<File> result = service.deleteFiles(Set.of());
        assertThat(result).isEmpty();
    }

    // === copyToClipboard / getClipboardFiles ===

    @Test
    void copyToClipboard_存储文件列表() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);

        service.copyToClipboard(List.of(f1, f2));

        assertThat(service.getClipboardFiles()).containsExactly(f1, f2);
    }

    @Test
    void getClipboardFiles_返回副本不影响内部状态() {
        File f1 = mock(File.class);
        service.copyToClipboard(List.of(f1));

        List<File> files = service.getClipboardFiles();
        files.clear();

        assertThat(service.getClipboardFiles()).hasSize(1);
    }

    @Test
    void copyToClipboard_覆盖之前的剪贴板内容() {
        File f1 = mock(File.class);
        File f2 = mock(File.class);

        service.copyToClipboard(List.of(f1));
        service.copyToClipboard(List.of(f2));

        assertThat(service.getClipboardFiles()).containsExactly(f2);
    }

    // === pasteFiles ===

    @Test
    void pasteFiles_复制剪贴板文件到目标目录() {
        File src = mock(File.class);
        File dest = mock(File.class);
        File targetDir = mock(File.class);

        service.copyToClipboard(List.of(src));
        when(fileRepository.copyFileTo(src, targetDir)).thenReturn(dest);

        List<File> result = service.pasteFiles(targetDir);

        assertThat(result).containsExactly(dest);
    }

    @Test
    void pasteFiles_剪贴板为空返回空列表() {
        File targetDir = mock(File.class);

        List<File> result = service.pasteFiles(targetDir);

        assertThat(result).isEmpty();
        verifyNoInteractions(fileRepository);
    }

    @Test
    void pasteFiles_目标目录为null返回空列表() {
        File src = mock(File.class);
        service.copyToClipboard(List.of(src));

        List<File> result = service.pasteFiles(null);

        assertThat(result).isEmpty();
    }

    @Test
    void pasteFiles_复制失败的文件不加入结果() {
        File src = mock(File.class);
        File targetDir = mock(File.class);

        service.copyToClipboard(List.of(src));
        when(fileRepository.copyFileTo(src, targetDir)).thenReturn(null);

        List<File> result = service.pasteFiles(targetDir);

        assertThat(result).isEmpty();
    }

    // === renameFile ===

    @Test
    void renameFile_委托给Repository() {
        File file = mock(File.class);
        when(fileRepository.renameFile(file, "new.txt")).thenReturn(true);

        boolean result = service.renameFile(file, "new.txt");

        assertThat(result).isTrue();
        verify(fileRepository).renameFile(file, "new.txt");
    }

    @Test
    void renameFile_重命名失败返回false() {
        File file = mock(File.class);
        when(fileRepository.renameFile(file, "new.txt")).thenReturn(false);

        boolean result = service.renameFile(file, "new.txt");

        assertThat(result).isFalse();
    }

    // === calculateDirStats ===

    @Test
    void calculateDirStats_计算图片数量和总大小() {
        File img = mock(File.class);
        File txt = mock(File.class);

        when(img.isFile()).thenReturn(true);
        when(img.exists()).thenReturn(true);
        when(img.length()).thenReturn(1024L);
        when(txt.isFile()).thenReturn(true);
        when(txt.exists()).thenReturn(true);
        when(txt.length()).thenReturn(500L);

        when(fileRepository.isImageFile(img)).thenReturn(true);
        when(fileRepository.isImageFile(txt)).thenReturn(false);

        long[] stats = service.calculateDirStats(List.of(img, txt));

        assertThat(stats[0]).isEqualTo(1); // 1 张图片
        assertThat(stats[1]).isEqualTo(1524L); // 总大小
    }

    @Test
    void calculateDirStats_null文件跳过() {
        File img = mock(File.class);
        when(img.isFile()).thenReturn(true);
        when(img.exists()).thenReturn(true);
        when(img.length()).thenReturn(100L);
        when(fileRepository.isImageFile(img)).thenReturn(true);

        long[] stats = service.calculateDirStats(Arrays.asList(null, img));

        assertThat(stats[0]).isEqualTo(1);
        assertThat(stats[1]).isEqualTo(100L);
    }

    @Test
    void calculateDirStats_不存在的文件跳过() {
        File file = mock(File.class);
        when(file.exists()).thenReturn(false);

        long[] stats = service.calculateDirStats(List.of(file));

        assertThat(stats[0]).isEqualTo(0);
        assertThat(stats[1]).isEqualTo(0L);
    }

    @Test
    void calculateDirStats_空列表返回零() {
        long[] stats = service.calculateDirStats(List.of());

        assertThat(stats[0]).isEqualTo(0);
        assertThat(stats[1]).isEqualTo(0L);
    }
}
