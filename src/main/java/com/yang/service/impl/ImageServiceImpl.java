package com.yang.service.impl;

import com.yang.repository.ImageRepository;
import com.yang.repository.impl.ImageRepositoryImpl;
import com.yang.service.ImageService;
import javafx.scene.image.Image;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 图片业务逻辑层实现
 */
@Service
public class ImageServiceImpl implements ImageService {

    private final ImageRepositoryImpl imageRepository;

    public ImageServiceImpl(ImageRepositoryImpl imageRepository) {
        this.imageRepository = imageRepository;
    }

    @Override
    public Image loadThumbnail(File file, int thumbSize) {
        return imageRepository.loadThumbnail(file, thumbSize);
    }

    @Override
    public Image loadFullImage(File file) {
        return imageRepository.loadFullImage(file);
    }

    @Override
    public List<String> getImagePaths(File directory) {
        return imageRepository instanceof com.yang.repository.impl.ImageRepositoryImpl impl
                ? List.of() : List.of();
    }

    @Override
    public boolean isImageFile(File file) {
        String lower = file.getName().toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".bmp");
    }

    @Override
    public void clearCache() {
        imageRepository.clearCache();
    }

    /**
     * 获取后台图片加载线程池
     */
    public ExecutorService getExecutor() {
        return imageRepository.getExecutor();
    }

    /**
     * 获取缓存的图片
     */
    public Image getCachedImage(String filePath) {
        return imageRepository.getCachedImage(filePath);
    }

    /**
     * 将图片放入缓存
     */
    public void cacheImage(String filePath, Image image) {
        imageRepository.cacheImage(filePath, image);
    }
}
