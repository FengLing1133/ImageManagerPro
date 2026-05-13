package com.yang.service.impl;

import com.yang.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
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
    void listChildDirectories_返回子目录() {
        new File(tempDir, "alpha").mkdir();
        new File(tempDir, "beta").mkdir();

        List<File> result = service.listChildDirectories(tempDir);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(File::getName)
                .containsExactly("alpha", "beta");
    }

    @Test
    void listChildDirectories_按名称排序() {
        new File(tempDir, "zebra").mkdir();
        new File(tempDir, "apple").mkdir();
        new File(tempDir, "mango").mkdir();

        List<File> result = service.listChildDirectories(tempDir);

        assertThat(result).extracting(File::getName)
                .containsExactly("apple", "mango", "zebra");
    }

    @Test
    void listChildDirectories_过滤系统目录() {
        new File(tempDir, "Windows").mkdir();
        new File(tempDir, "$Recycle.Bin").mkdir();
        new File(tempDir, "Users").mkdir();

        List<File> result = service.listChildDirectories(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Users");
    }

    @Test
    void listChildDirectories_空目录返回空列表() {
        List<File> result = service.listChildDirectories(tempDir);
        assertThat(result).isEmpty();
    }

    // === getSystemRoots ===

    @Test
    void getSystemRoots_返回非空数组() {
        File[] roots = service.getSystemRoots();
        assertThat(roots).isNotEmpty();
    }

    // === getPicturesDirectory ===

    @Test
    void getPicturesDirectory_返回用户图片目录或null() {
        File result = service.getPicturesDirectory();
        // 结果取决于测试环境是否有 Pictures 目录
        if (result != null) {
            assertThat(result.isDirectory()).isTrue();
            assertThat(result.getName()).isEqualTo("Pictures");
        }
    }

    // === isSystemDirectory ===

    @Test
    void isSystemDirectory_Windows目录返回true() {
        File dir = new File(tempDir, "Windows");
        assertThat(service.isSystemDirectory(dir)).isTrue();
    }

    @Test
    void isSystemDirectory_RecycleBin返回true() {
        File dir = new File(tempDir, "$Recycle.Bin");
        assertThat(service.isSystemDirectory(dir)).isTrue();
    }

    @Test
    void isSystemDirectory_普通目录返回false() {
        File dir = new File(tempDir, "MyFolder");
        assertThat(service.isSystemDirectory(dir)).isFalse();
    }
}
