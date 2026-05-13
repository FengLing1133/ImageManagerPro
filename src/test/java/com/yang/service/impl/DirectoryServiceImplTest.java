package com.yang.service.impl;

import com.yang.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceImplTest {

    @Mock
    private FileRepository fileRepository;

    private DirectoryServiceImpl service;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        service = new DirectoryServiceImpl(fileRepository);
    }

    // === listChildDirectories ===

    @Test
    @DisplayName("listChildDirectories: 返回子目录")
    void listChildDirectories_returnsChildDirectories() {
        new File(tempDir, "alpha").mkdir();
        new File(tempDir, "beta").mkdir();

        List<File> result = service.listChildDirectories(tempDir);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(File::getName)
                .containsExactly("alpha", "beta");
    }

    @Test
    @DisplayName("listChildDirectories: 按名称排序")
    void listChildDirectories_sortedByName() {
        new File(tempDir, "zebra").mkdir();
        new File(tempDir, "apple").mkdir();
        new File(tempDir, "mango").mkdir();

        List<File> result = service.listChildDirectories(tempDir);

        assertThat(result).extracting(File::getName)
                .containsExactly("apple", "mango", "zebra");
    }

    @Test
    @DisplayName("listChildDirectories: 过滤系统目录")
    void listChildDirectories_filtersSystemDirectories() {
        new File(tempDir, "Windows").mkdir();
        new File(tempDir, "$Recycle.Bin").mkdir();
        new File(tempDir, "Users").mkdir();

        List<File> result = service.listChildDirectories(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Users");
    }

    @Test
    @DisplayName("listChildDirectories: 空目录返回空列表")
    void listChildDirectories_emptyDirectoryReturnsEmptyList() {
        List<File> result = service.listChildDirectories(tempDir);
        assertThat(result).isEmpty();
    }

    // === getSystemRoots ===

    @Test
    @DisplayName("getSystemRoots: 返回非空数组")
    void getSystemRoots_returnsNonEmptyArray() {
        File[] roots = service.getSystemRoots();
        assertThat(roots).isNotEmpty();
    }

    // === getPicturesDirectory ===

    @Test
    @DisplayName("getPicturesDirectory: 返回用户图片目录或null")
    void getPicturesDirectory_returnsPicturesDirOrNull() {
        File result = service.getPicturesDirectory();
        // 结果取决于测试环境是否有 Pictures 目录
        if (result != null) {
            assertThat(result.isDirectory()).isTrue();
            assertThat(result.getName()).isEqualTo("Pictures");
        }
    }

    // === isSystemDirectory ===

    @Test
    @DisplayName("isSystemDirectory: Windows目录返回true")
    void isSystemDirectory_windowsDirReturnsTrue() {
        File dir = new File(tempDir, "Windows");
        assertThat(service.isSystemDirectory(dir)).isTrue();
    }

    @Test
    @DisplayName("isSystemDirectory: RecycleBin返回true")
    void isSystemDirectory_recycleBinReturnsTrue() {
        File dir = new File(tempDir, "$Recycle.Bin");
        assertThat(service.isSystemDirectory(dir)).isTrue();
    }

    @Test
    @DisplayName("isSystemDirectory: 普通目录返回false")
    void isSystemDirectory_normalDirReturnsFalse() {
        File dir = new File(tempDir, "MyFolder");
        assertThat(service.isSystemDirectory(dir)).isFalse();
    }
}
