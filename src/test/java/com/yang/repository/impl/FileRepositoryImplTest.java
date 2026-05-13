package com.yang.repository.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileRepositoryImplTest {

    private FileRepositoryImpl repository;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        repository = new FileRepositoryImpl();
    }

    // === listVisibleFiles ===

    @Test
    void listVisibleFiles_返回可见文件和目录() throws IOException {
        File visible = new File(tempDir, "test.txt");
        visible.createNewFile();
        File hidden = new File(tempDir, ".hidden");
        hidden.createNewFile();

        List<File> result = repository.listVisibleFiles(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test.txt");
    }

    @Test
    void listVisibleFiles_目录排在文件前面() throws IOException {
        File dir = new File(tempDir, "adir");
        dir.mkdir();
        File file = new File(tempDir, "afile.txt");
        file.createNewFile();

        List<File> result = repository.listVisibleFiles(tempDir);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isDirectory()).isTrue();
        assertThat(result.get(1).isFile()).isTrue();
    }

    @Test
    void listVisibleFiles_按名称不区分大小写排序() throws IOException {
        new File(tempDir, "Banana.txt").createNewFile();
        new File(tempDir, "apple.txt").createNewFile();
        new File(tempDir, "Cherry.txt").createNewFile();

        List<File> result = repository.listVisibleFiles(tempDir);

        assertThat(result).extracting(File::getName)
                .containsExactly("apple.txt", "Banana.txt", "Cherry.txt");
    }

    @Test
    void listVisibleFiles_空目录返回空列表() {
        List<File> result = repository.listVisibleFiles(tempDir);
        assertThat(result).isEmpty();
    }

    // === deleteFile ===

    @Test
    void deleteFile_删除存在的文件返回true() throws IOException {
        File file = new File(tempDir, "toDelete.txt");
        file.createNewFile();

        boolean result = repository.deleteFile(file);

        assertThat(result).isTrue();
        assertThat(file).doesNotExist();
    }

    @Test
    void deleteFile_删除不存在的文件返回false() {
        File file = new File(tempDir, "nonexistent.txt");

        boolean result = repository.deleteFile(file);

        assertThat(result).isFalse();
    }

    // === renameFile ===

    @Test
    void renameFile_重命名成功返回true() throws IOException {
        File file = new File(tempDir, "old.txt");
        file.createNewFile();

        boolean result = repository.renameFile(file, "new.txt");

        assertThat(result).isTrue();
        assertThat(new File(tempDir, "new.txt")).exists();
        assertThat(file).doesNotExist();
    }

    @Test
    void renameFile_目标已存在时返回false() throws IOException {
        File old = new File(tempDir, "old.txt");
        old.createNewFile();
        new File(tempDir, "new.txt").createNewFile();

        boolean result = repository.renameFile(old, "new.txt");

        assertThat(result).isFalse();
    }

    // === copyFileTo ===

    @Test
    void copyFileTo_复制文件到目标目录() throws IOException {
        File src = new File(tempDir, "source.txt");
        Files.writeString(src.toPath(), "hello");
        File destDir = new File(tempDir, "dest");
        destDir.mkdir();

        File result = repository.copyFileTo(src, destDir);

        assertThat(result).isNotNull();
        assertThat(result).exists();
        assertThat(result.getName()).isEqualTo("source.txt");
        assertThat(Files.readString(result.toPath())).isEqualTo("hello");
    }

    @Test
    void copyFileTo_目标已存在时自动添加数字后缀() throws IOException {
        File src = new File(tempDir, "file.txt");
        Files.writeString(src.toPath(), "original");
        File destDir = new File(tempDir, "dest");
        destDir.mkdir();
        new File(destDir, "file.txt").createNewFile();

        File result = repository.copyFileTo(src, destDir);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("file(1).txt");
    }

    @Test
    void copyFileTo_多个同名文件依次添加后缀() throws IOException {
        File src = new File(tempDir, "dup.txt");
        Files.writeString(src.toPath(), "data");
        File destDir = new File(tempDir, "dest");
        destDir.mkdir();
        new File(destDir, "dup.txt").createNewFile();
        new File(destDir, "dup(1).txt").createNewFile();

        File result = repository.copyFileTo(src, destDir);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("dup(2).txt");
    }

    // === isImageFile ===

    @Test
    void isImageFile_jpg文件返回true() throws IOException {
        File file = new File(tempDir, "photo.jpg");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isTrue();
    }

    @Test
    void isImageFile_png文件返回true() throws IOException {
        File file = new File(tempDir, "image.png");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isTrue();
    }

    @Test
    void isImageFile_大写扩展名返回true() throws IOException {
        File file = new File(tempDir, "PHOTO.JPG");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isTrue();
    }

    @Test
    void isImageFile_txt文件返回false() throws IOException {
        File file = new File(tempDir, "readme.txt");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isFalse();
    }

    @Test
    void isImageFile_目录返回false() {
        File dir = new File(tempDir, "photos");
        dir.mkdir();
        assertThat(repository.isImageFile(dir)).isFalse();
    }

    // === isVisibleFile ===

    @Test
    void isVisibleFile_普通文件返回true() throws IOException {
        File file = new File(tempDir, "normal.txt");
        file.createNewFile();
        assertThat(repository.isVisibleFile(file)).isTrue();
    }

    @Test
    void isVisibleFile_点开头文件返回false() throws IOException {
        File file = new File(tempDir, ".config");
        file.createNewFile();
        assertThat(repository.isVisibleFile(file)).isFalse();
    }

    // === formatSize ===

    @Test
    void formatSize_字节() {
        assertThat(repository.formatSize(500)).isEqualTo("500 B");
    }

    @Test
    void formatSize_千字节() {
        assertThat(repository.formatSize(1024)).isEqualTo("1.0 KB");
    }

    @Test
    void formatSize_兆字节() {
        assertThat(repository.formatSize(1048576)).isEqualTo("1.0 MB");
    }

    @Test
    void formatSize_吉字节() {
        assertThat(repository.formatSize(1073741824)).isEqualTo("1.0 GB");
    }

    @Test
    void formatSize_零字节() {
        assertThat(repository.formatSize(0)).isEqualTo("0 B");
    }

    // === getImagePaths ===

    @Test
    void getImagePaths_返回图片文件路径() throws IOException {
        new File(tempDir, "a.jpg").createNewFile();
        new File(tempDir, "b.png").createNewFile();
        new File(tempDir, "c.txt").createNewFile();

        List<String> result = repository.getImagePaths(tempDir);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.endsWith(".jpg") || p.endsWith(".png"));
    }

    @Test
    void getImagePaths_按路径排序() throws IOException {
        new File(tempDir, "c.jpg").createNewFile();
        new File(tempDir, "a.jpg").createNewFile();
        new File(tempDir, "b.jpg").createNewFile();

        List<String> result = repository.getImagePaths(tempDir);

        assertThat(result).isSorted();
    }

    @Test
    void getImagePaths_无图片返回空列表() throws IOException {
        new File(tempDir, "readme.txt").createNewFile();

        List<String> result = repository.getImagePaths(tempDir);

        assertThat(result).isEmpty();
    }
}
