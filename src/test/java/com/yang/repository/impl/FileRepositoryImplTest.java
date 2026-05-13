package com.yang.repository.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("listVisibleFiles: 返回可见文件和目录")
    void listVisibleFiles_returnsVisibleFilesAndDirectories() throws IOException {
        File visible = new File(tempDir, "test.txt");
        visible.createNewFile();
        File hidden = new File(tempDir, ".hidden");
        hidden.createNewFile();

        List<File> result = repository.listVisibleFiles(tempDir);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test.txt");
    }

    @Test
    @DisplayName("listVisibleFiles: 目录排在文件前面")
    void listVisibleFiles_directoriesBeforeFiles() throws IOException {
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
    @DisplayName("listVisibleFiles: 按名称不区分大小写排序")
    void listVisibleFiles_sortedByNameCaseInsensitive() throws IOException {
        new File(tempDir, "Banana.txt").createNewFile();
        new File(tempDir, "apple.txt").createNewFile();
        new File(tempDir, "Cherry.txt").createNewFile();

        List<File> result = repository.listVisibleFiles(tempDir);

        assertThat(result).extracting(File::getName)
                .containsExactly("apple.txt", "Banana.txt", "Cherry.txt");
    }

    @Test
    @DisplayName("listVisibleFiles: 空目录返回空列表")
    void listVisibleFiles_emptyDirectoryReturnsEmptyList() {
        List<File> result = repository.listVisibleFiles(tempDir);
        assertThat(result).isEmpty();
    }

    // === deleteFile ===

    @Test
    @DisplayName("deleteFile: 删除存在的文件返回true")
    void deleteFile_existingFileReturnsTrue() throws IOException {
        File file = new File(tempDir, "toDelete.txt");
        file.createNewFile();

        boolean result = repository.deleteFile(file);

        assertThat(result).isTrue();
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("deleteFile: 删除不存在的文件返回false")
    void deleteFile_nonExistentFileReturnsFalse() {
        File file = new File(tempDir, "nonexistent.txt");

        boolean result = repository.deleteFile(file);

        assertThat(result).isFalse();
    }

    // === renameFile ===

    @Test
    @DisplayName("renameFile: 重命名成功返回true")
    void renameFile_successReturnsTrue() throws IOException {
        File file = new File(tempDir, "old.txt");
        file.createNewFile();

        boolean result = repository.renameFile(file, "new.txt");

        assertThat(result).isTrue();
        assertThat(new File(tempDir, "new.txt")).exists();
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("renameFile: 目标已存在时返回false")
    void renameFile_targetAlreadyExistsReturnsFalse() throws IOException {
        File old = new File(tempDir, "old.txt");
        old.createNewFile();
        new File(tempDir, "new.txt").createNewFile();

        boolean result = repository.renameFile(old, "new.txt");

        assertThat(result).isFalse();
    }

    // === copyFileTo ===

    @Test
    @DisplayName("copyFileTo: 复制文件到目标目录")
    void copyFileTo_copiesFileToTargetDirectory() throws IOException {
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
    @DisplayName("copyFileTo: 目标已存在时自动添加数字后缀")
    void copyFileTo_autoAppendsNumericSuffixWhenTargetExists() throws IOException {
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
    @DisplayName("copyFileTo: 多个同名文件依次添加后缀")
    void copyFileTo_appendsIncrementalSuffixForMultipleDuplicates() throws IOException {
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
    @DisplayName("isImageFile: jpg文件返回true")
    void isImageFile_jpgReturnsTrue() throws IOException {
        File file = new File(tempDir, "photo.jpg");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isTrue();
    }

    @Test
    @DisplayName("isImageFile: png文件返回true")
    void isImageFile_pngReturnsTrue() throws IOException {
        File file = new File(tempDir, "image.png");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isTrue();
    }

    @Test
    @DisplayName("isImageFile: 大写扩展名返回true")
    void isImageFile_upperCaseExtensionReturnsTrue() throws IOException {
        File file = new File(tempDir, "PHOTO.JPG");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isTrue();
    }

    @Test
    @DisplayName("isImageFile: txt文件返回false")
    void isImageFile_txtReturnsFalse() throws IOException {
        File file = new File(tempDir, "readme.txt");
        file.createNewFile();
        assertThat(repository.isImageFile(file)).isFalse();
    }

    @Test
    @DisplayName("isImageFile: 目录返回false")
    void isImageFile_directoryReturnsFalse() {
        File dir = new File(tempDir, "photos");
        dir.mkdir();
        assertThat(repository.isImageFile(dir)).isFalse();
    }

    // === isVisibleFile ===

    @Test
    @DisplayName("isVisibleFile: 普通文件返回true")
    void isVisibleFile_normalFileReturnsTrue() throws IOException {
        File file = new File(tempDir, "normal.txt");
        file.createNewFile();
        assertThat(repository.isVisibleFile(file)).isTrue();
    }

    @Test
    @DisplayName("isVisibleFile: 点开头文件返回false")
    void isVisibleFile_dotPrefixFileReturnsFalse() throws IOException {
        File file = new File(tempDir, ".config");
        file.createNewFile();
        assertThat(repository.isVisibleFile(file)).isFalse();
    }

    // === formatSize ===

    @Test
    @DisplayName("formatSize: 字节")
    void formatSize_bytes() {
        assertThat(repository.formatSize(500)).isEqualTo("500 B");
    }

    @Test
    @DisplayName("formatSize: 千字节")
    void formatSize_kilobytes() {
        assertThat(repository.formatSize(1024)).isEqualTo("1.0 KB");
    }

    @Test
    @DisplayName("formatSize: 兆字节")
    void formatSize_megabytes() {
        assertThat(repository.formatSize(1048576)).isEqualTo("1.0 MB");
    }

    @Test
    @DisplayName("formatSize: 吉字节")
    void formatSize_gigabytes() {
        assertThat(repository.formatSize(1073741824)).isEqualTo("1.0 GB");
    }

    @Test
    @DisplayName("formatSize: 零字节")
    void formatSize_zeroBytes() {
        assertThat(repository.formatSize(0)).isEqualTo("0 B");
    }

    // === getImagePaths ===

    @Test
    @DisplayName("getImagePaths: 返回图片文件路径")
    void getImagePaths_returnsImageFilePaths() throws IOException {
        new File(tempDir, "a.jpg").createNewFile();
        new File(tempDir, "b.png").createNewFile();
        new File(tempDir, "c.txt").createNewFile();

        List<String> result = repository.getImagePaths(tempDir);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.endsWith(".jpg") || p.endsWith(".png"));
    }

    @Test
    @DisplayName("getImagePaths: 按路径排序")
    void getImagePaths_sortedByPath() throws IOException {
        new File(tempDir, "c.jpg").createNewFile();
        new File(tempDir, "a.jpg").createNewFile();
        new File(tempDir, "b.jpg").createNewFile();

        List<String> result = repository.getImagePaths(tempDir);

        assertThat(result).isSorted();
    }

    @Test
    @DisplayName("getImagePaths: 无图片返回空列表")
    void getImagePaths_noImagesReturnsEmptyList() throws IOException {
        new File(tempDir, "readme.txt").createNewFile();

        List<String> result = repository.getImagePaths(tempDir);

        assertThat(result).isEmpty();
    }
}
