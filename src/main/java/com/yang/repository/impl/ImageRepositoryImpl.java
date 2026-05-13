package com.yang.repository.impl;

import com.yang.repository.ImageRepository;
import javafx.scene.image.Image;
import org.springframework.stereotype.Repository;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 图片数据访问层实现
 * 封装图片加载、缩略图生成和 LRU 缓存管理
 */
@Repository
public class ImageRepositoryImpl implements ImageRepository {

    private static final int CACHE_MAX_SIZE = 200;

    private final Map<String, Image> imageCache = Collections.synchronizedMap(
            new LinkedHashMap<>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("Image Loader");
        return thread;
    });

    @Override
    public Image loadThumbnail(File file, int thumbSize) {
        String filePath = file.getAbsolutePath();
        Image cached = imageCache.get(filePath);
        if (cached != null) return cached;

        try {
            Image img = new Image(file.toURI().toString(), thumbSize, thumbSize, true, true, false);
            if (!img.isError()) {
                imageCache.put(filePath, img);
                return img;
            }
        } catch (Exception e) {
            // 加载失败返回 null
        }
        return null;
    }

    @Override
    public Image loadFullImage(File file) {
        try {
            Image image = new Image(file.toURI().toString(), false);
            if (!image.isError()) {
                return image;
            }
        } catch (Exception e) {
            // 加载失败返回 null
        }
        return null;
    }

    @Override
    public Image getCachedImage(String filePath) {
        return imageCache.get(filePath);
    }

    @Override
    public void cacheImage(String filePath, Image image) {
        imageCache.put(filePath, image);
    }

    @Override
    public void clearCache() {
        imageCache.clear();
    }

    @Override
    public ExecutorService getExecutor() {
        return imageExecutor;
    }

    @Override
    public void shutdown() {
        imageExecutor.shutdown();
    }

    @Override
    public List<String> getImagePaths(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        return Arrays.stream(files)
                .filter(File::isFile)
                .filter(f -> {
                    String lower = f.getName().toLowerCase();
                    return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                            || lower.endsWith(".png") || lower.endsWith(".gif")
                            || lower.endsWith(".bmp");
                })
                .map(File::getAbsolutePath)
                .sorted()
                .collect(Collectors.toList());
    }
}
