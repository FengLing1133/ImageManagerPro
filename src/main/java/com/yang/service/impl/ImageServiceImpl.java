package com.yang.service.impl;

import com.yang.repository.FileRepository;
import com.yang.repository.ImageRepository;
import com.yang.service.ImageService;
import javafx.scene.image.Image;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

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

    /** 根据扩展名判断是否为支持的图片格式 */
    @Override
    public boolean isImageFile(File file) {
        String lower = file.getName().toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".bmp");
    }

    /** 清空图片缓存 */
    @Override
    public void clearCache() {
        imageRepository.clearCache();
    }

    /** 获取图片加载线程池 */
    @Override
    public ExecutorService getExecutor() {
        return imageRepository.getExecutor();
    }

    /** 从缓存中获取图片 */
    @Override
    public Image getCachedImage(String filePath) {
        return imageRepository.getCachedImage(filePath);
    }

    /** 将图片存入缓存 */
    @Override
    public void cacheImage(String filePath, Image image) {
        imageRepository.cacheImage(filePath, image);
    }
}
