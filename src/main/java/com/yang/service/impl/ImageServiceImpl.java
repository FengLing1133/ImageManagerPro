package com.yang.service.impl;

import com.yang.repository.ImageRepository;
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

    private final ImageRepository imageRepository;

    public ImageServiceImpl(ImageRepository imageRepository) {
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
        return imageRepository.getImagePaths(directory);
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

    @Override
    public ExecutorService getExecutor() {
        return imageRepository.getExecutor();
    }

    @Override
    public Image getCachedImage(String filePath) {
        return imageRepository.getCachedImage(filePath);
    }

    @Override
    public void cacheImage(String filePath, Image image) {
        imageRepository.cacheImage(filePath, image);
    }
}
