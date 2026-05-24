package com.yang.service.impl;

import com.yang.repository.FileRepository;
import com.yang.repository.ImageRepository;
import com.yang.service.ImageService;
import javafx.scene.image.Image;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.List;

/** 图片业务逻辑层实现 */
@Service
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private final FileRepository fileRepository;

    public ImageServiceImpl(ImageRepository imageRepository, FileRepository fileRepository) {
        this.imageRepository = imageRepository;
        this.fileRepository = fileRepository;
    }

    /** 加载缩略图，委托给 ImageRepository */
    @Override
    public Image loadThumbnail(File file, int thumbSize) {
        return imageRepository.loadThumbnail(file, thumbSize);
    }

    /** 加载原图 */
    @Override
    public Image loadFullImage(File file) {
        return imageRepository.loadFullImage(file);
    }

    /** 获取目录下所有图片文件路径 */
    @Override
    public List<String> getImagePaths(File directory) {
        return fileRepository.getImagePaths(directory);
    }

    /** 根据扩展名判断是否为支持的图片格式，委托给 FileRepository */
    @Override
    public boolean isImageFile(File file) {
        return fileRepository.isImageFile(file);
    }

    /** 清空图片缓存 */
    @Override
    public void clearCache() {
        imageRepository.clearCache();
    }

    /** 缓存检查 + 加载一体化 */
    @Override
    public Image loadImage(File file, int thumbSize) {
        return imageRepository.loadThumbnail(file, thumbSize);
    }

    /** 提交图片加载任务到后台线程池 */
    @Override
    public void submitImageLoadTask(Runnable task) {
        imageRepository.getExecutor().submit(task);
    }

    /** 关闭线程池和清理资源 */
    @Override
    public void shutdown() {
        imageRepository.shutdown();
        imageRepository.clearCache();
    }
}
